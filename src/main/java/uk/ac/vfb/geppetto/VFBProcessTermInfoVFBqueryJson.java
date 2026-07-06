package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.DataSource;

import org.geppetto.core.model.GeppettoModelAccess;

import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.types.Type;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.Text;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.ImportType;
import org.geppetto.model.datasources.DataSourceLibraryConfiguration;

/**
 * Term-info processor for the VFBquery get_term_info JSON shape.
 *
 * Sibling of {@link VFBProcessTermInfoCachedJson}: it consumes the already-
 * processed display model emitted by VFBquery (Meta.*, Synonyms[], Xrefs[],
 * Licenses{}, Images{}, Queries[]) instead of the raw SOLR term_info envelope,
 * and rebuilds the SAME Geppetto term-info CompositeType (one HTML Variable per
 * row, same reference ids the frontend keys on). VFBquery has already done the
 * data assembly and emits markdown links ([label](id)); this processor converts
 * those to the old intLink HTML and renders each row.
 *
 * The available-query list and per-query count badge are sourced from VFBquery's
 * Queries[] array (single source of truth, gives the count badge for free).
 *
 * @author robertcourt (VFBquery term-info migration, Phase 3)
 */
public class VFBProcessTermInfoVFBqueryJson extends AQueryProcessor {

	private Boolean debug=false;

	/* Hard-coded template display names (symbol, or label where no symbol exists).
	   VFB has a small, rarely-changing set of templates and the per-term payload does
	   not carry the template's own label, so resolve the name here. */
	private static final java.util.List<String> AVAILABLE_TEMPLATES = java.util.Arrays.asList(
			"VFB_00017894", "VFB_00101567", "VFB_00101384", "VFB_00050000",
			"VFB_00049000", "VFB_00100000", "VFB_00030786", "VFB_00200000");
	private static final java.util.Map<String, String> TEMPLATE_NAMES = new java.util.HashMap<String, String>();
	static {
		TEMPLATE_NAMES.put("VFB_00017894", "JFRC2");
		TEMPLATE_NAMES.put("VFB_00101567", "JRC2018U");
		TEMPLATE_NAMES.put("VFB_00101384", "JRCFIB2018Fum");
		TEMPLATE_NAMES.put("VFB_00050000", "L1 larval CNS ssTEM");
		TEMPLATE_NAMES.put("VFB_00049000", "L3 CNS template - Wood2018");
		TEMPLATE_NAMES.put("VFB_00100000", "COURT2018VNS");
		TEMPLATE_NAMES.put("VFB_00030786", "adult brain template Ito2014");
		TEMPLATE_NAMES.put("VFB_00200000", "JRCVNC2018U");
	}

	private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
	// Leading "NN%" confidence prefix on a relationship (e.g. "89% capable of").
	private static final Pattern CONF_PREFIX = Pattern.compile("^(\\d+%)\\s+(.*)$", Pattern.DOTALL);
	// Trailing "(...refs...)" group of markdown links appended to an enriched
	// relationship -- the reference(s) supporting the confidence assertion.
	private static final Pattern TRAILING_REF = Pattern.compile(
			"^(.*?)\\s*\\(((?:\\[[^\\]]+\\]\\([^)]+\\)(?:,\\s*)?)+)\\)\\s*$", Pattern.DOTALL);
	private static final Pattern MD_IMAGE = Pattern.compile("\\[!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+'([^']*)')?\\)\\]\\(([^)]+)\\)");

	// ---- markdown -> HTML helpers (reproduce the old intLink format) --------

	/** [LABEL](SF) -> <a href="?id=SF" data-instancepath="SF">LABEL</a>. Plain
	 *  (non-link) text is passed through unchanged. Image markdown is skipped
	 *  here (handled by the image rows). */
	private static String mdToHtml(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		Matcher m = MD_LINK.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String label = m.group(1);
			String target = m.group(2);
			String repl;
			if (target.startsWith("http://") || target.startsWith("https://")) {
				// External reference (e.g. a relationship's database_cross_reference
				// such as "FlyBase:FBrf...") -- render as an external linkout, using
				// the site-typed icon where the label carries a known prefix, so the
				// neurotransmitter reference shows the fly/DOI/PubMed icon as it did
				// before the VFBquery migration (rather than a broken ?id= link).
				String icon = referenceIcon(label);
				String inner = icon.isEmpty() ? Matcher.quoteReplacement(label) : icon;
				repl = "<a href=\"" + target + "\" target=\"_blank\" title=\"" + label + "\">"
						+ inner + "</a>";
			} else {
				// Internal VFB term: target may be "TEMPLATE,SF" for image cells; take the last id.
				String sf = target.contains(",") ? target.substring(target.lastIndexOf(',') + 1) : target;
				repl = "<a href=\"?id=" + sf + "\" data-instancepath=\"" + sf + "\">"
						+ Matcher.quoteReplacement(label) + "</a>";
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(repl));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** Site-typed icon for a "SITE:accession" reference label, matching the old
	 *  cached-JSON rendering (fly for FlyBase, etc.); "" if the prefix is unknown. */
	private static String referenceIcon(String ref) {
		if (ref == null) {
			return "";
		}
		String low = ref.toLowerCase();
		if (low.startsWith("doi:")) {
			return "<i class=\"popup-icon-link gpt-doi\"></i>";
		}
		if (low.startsWith("flybase:")) {
			return "<i class=\"popup-icon-link gpt-fly\"></i>";
		}
		if (low.startsWith("pmid:") || low.startsWith("pubmed:")) {
			return "<i class=\"popup-icon-link gpt-pubmed\"></i>";
		}
		if (low.startsWith("go_ref:")) {
			return "<i class=\"popup-icon-link gpt-geneontology\"></i>";
		}
		return "";
	}

	/** Convert a VFBquery Meta.* string ("[rel](id): [a](id), [b](id); ...")
	 *  to the old list HTML: <ul class="terminfo-CSS"><li>...</li></ul>. */
	// Relationships render the RELATION as plain text (the relation-ontology id,
	// e.g. RO_/BFO_/PATO_, is not VFB-browsable so must not be a link) and the
	// object(s) after the first ":" as links, mirroring v2.
	private static String relationshipsToHtml(String metaValue) {
		if (metaValue == null || metaValue.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("<ul class=\"terminfo-relationships\">");
		for (String seg : metaValue.split(";")) {
			seg = seg.trim();
			if (seg.isEmpty()) continue;

			// Leading confidence -> linked grey badge (as in v2).
			String badge = "";
			Matcher cm = CONF_PREFIX.matcher(seg);
			if (cm.matches()) {
				badge = confidenceBadge(cm.group(1));
				seg = cm.group(2).trim();
			}
			// Trailing "(...refs...)" is the reference for the confidence assertion,
			// so render it as icon linkout(s) placed with the badge (before the
			// relation), matching v2 -- not trailing after the object.
			String refs = "";
			Matcher rm = TRAILING_REF.matcher(seg);
			if (rm.matches()) {
				refs = mdToHtml(rm.group(2));
				seg = rm.group(1).trim();
			}

			String body;
			int colon = seg.indexOf(':');
			if (colon > 0) {
				String rel = stripLinks(seg.substring(0, colon).trim());
				String objs = mdToHtml(seg.substring(colon + 1).trim());
				body = rel + ": " + objs;
			} else {
				body = mdToHtml(seg);
			}

			sb.append("<li>");
			if (!badge.isEmpty()) {
				sb.append(badge).append(" ");
			}
			if (!refs.isEmpty()) {
				sb.append(refs).append(" ");
			}
			sb.append(body).append("</li>");
		}
		sb.append("</ul>");
		return sb.toString();
	}

	/** v2 confidence badge: a grey pill linked to the confidence-value docs. */
	private static String confidenceBadge(String pct) {
		return "<a href=\"https://virtualflybrain.org/docs/concepts/confidence-value/\""
				+ " target=\"_blank\" title=\"confidence value\">"
				+ "<span class=\"badge badge-secondary\" title=\"confidence value\">" + pct + "</span></a>";
	}

	private static String metaListToHtml(String metaValue, String css) {
		if (metaValue == null || metaValue.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("<ul class=\"terminfo-" + css + "\">");
		for (String seg : metaValue.split(";")) {
			seg = seg.trim();
			if (!seg.isEmpty()) {
				sb.append("<li>").append(mdToHtml(seg)).append("</li>");
			}
		}
		sb.append("</ul>");
		return sb.toString();
	}

	private static String secureUrl(String url) {
		return url == null ? "" : url.replace("http://", "https://");
	}

	/** Round-down compact count: 999, 1.2K, 9.9K, 12K, 226K, 1.5M, 2B. */
	static String formatCount(long n) {
		// Floor to the smallest compact representation: 46789 -> 46k, 527179 -> 527k,
		// 1_000_000 -> 1M. Integer division floors for non-negative n.
		if (n < 1000L) {
			return Long.toString(n);
		}
		if (n < 1000000L) {
			return (n / 1000L) + "k";
		}
		if (n < 1000000000L) {
			return (n / 1000000L) + "M";
		}
		return (n / 1000000000L) + "B";
	}

	// ---- VFBquery JSON POJOs (subset of get_term_info we render) -------------

	private class Synonym {
		String label;
		String scope;
		String type;
		String publication;
	}

	private class Xref {
		String label;
		String accession;
		String link;
		String icon;
	}

	private class License {
		String iri;
		String short_form;
		String label;
		String icon;
		String source;
		String source_iri;
	}

	private class ImageRec {
		String id;
		String label;
		String thumbnail;
		String thumbnail_transparent;
		String nrrd;
		String obj;
		String wlz;
		String swc;
		Double index;
		String orientation;
		XYZ center;
		XYZ extent;
		XYZ voxel;
	}

	private class XYZ {
		Double X;
		Double Y;
		Double Z;
	}

	private class Publication {
		String title;
		String short_form;
		String microref;
		List<String> refs;
	}

	private class Query {
		String query;            // query type id, e.g. "SplitsTargeting"
		String label;            // display label
		Double count;            // result count (JSON sends 46.0; null/-1 = deferred)
	}

	// ---- main process -------------------------------------------------------

	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable,
			QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {
		try {
			if (results == null || results.getValue("term_info", 0) == null) {
				return results;
			}
			String json = results.getValue("term_info", 0).toString();
			// NB: gson.fromJson(json, JsonObject.class) returns an EMPTY JsonObject in
			// this OSGi/Gson environment (confirmed via the debug log: valid json head
			// but zero top-level keys). JsonParser builds the tree directly and works,
			// matching the typed-POJO fromJson the legacy processor relies on.
			JsonObject ti = new JsonParser().parse(json).getAsJsonObject();
			if (ti == null) {
				return results;
			}
			// Unconditional diagnostic: the v2-dev build target is "release", so the
			// Dockerfile sed leaves debug=false; log the received container shape
			// regardless so a mishandled term can be inspected from the server log.
			if (debug) {
				StringBuilder dbgKeys = new StringBuilder();
				for (Map.Entry<String, JsonElement> de : ti.entrySet()) {
					dbgKeys.append(de.getKey()).append(' ');
				}
				System.out.println("VFBProcessTermInfoVFBqueryJson: raw term_info top-level keys=["
						+ dbgKeys.toString().trim() + "] head="
						+ json.substring(0, Math.min(180, json.length())));
			}
			// get_term_info returns the term keyed by its short_form, e.g.
			// {"VFB_00101567": {Id, Name, ...}}. Unwrap to the inner term object
			// (the variable id key if present, else a single id-keyed object).
			if (!ti.has("Id") && !ti.has("Name")) {
				JsonElement keyed = ti.has(variable.getId()) ? ti.get(variable.getId()) : null;
				if (keyed == null && ti.entrySet().size() == 1) {
					keyed = ti.entrySet().iterator().next().getValue();
				}
				if (keyed != null && keyed.isJsonObject()
						&& (keyed.getAsJsonObject().has("Id") || keyed.getAsJsonObject().has("Name"))) {
					ti = keyed.getAsJsonObject();
				}
			}

			String tempId = variable.getId();
			List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();

			JsonObject meta = ti.has("Meta") && ti.get("Meta").isJsonObject() ? ti.getAsJsonObject("Meta") : new JsonObject();
			String id = optStr(ti, "Id");
			// Bold the term LABEL (from Meta.Name markdown) -- for templates the
			// top-level Name is the short symbol, but v2 shows the label.
			String name = symbolText(optStr(meta, "Name"));
			if (name.isEmpty()) name = optStr(ti, "Name");
			List<String> superTypes = strList(ti, "SuperTypes");
			String typeString = typesString(strList(ti, "Tags"));
			String tempName = (name != null && !name.isEmpty()) ? name : tempId;

			// Connect the metadata to the fetched variable, mirroring
			// VFBProcessTermInfoCachedJson: variable -> (anonymousType) parentType
			// -> (variable) metaDataVar -> (type) metaDataType -> HTML rows. Without
			// this the term variable has no attached metadata and the client crashes
			// processing the runtime tree.
			geppettoModelAccess.setObjectAttribute(variable, GeppettoPackage.Literals.NODE__NAME, tempName);

			CompositeType parentType = TypesFactory.eINSTANCE.createCompositeType();
			parentType.setId(tempId);
			variable.getAnonymousTypes().add(parentType);

			CompositeType metaDataType = TypesFactory.eINSTANCE.createCompositeType();
			Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
			metaDataVar.getTypes().add(metaDataType);
			metaDataVar.setId(tempId + "_meta");
			metaDataVar.setName(tempName);
			metaDataType.setId(tempId + "_metadata");
			metaDataType.setName("Info");
			geppettoModelAccess.addVariableToType(metaDataVar, parentType);
			geppettoModelAccess.addTypeToLibrary(metaDataType, dataSource.getTargetLibrary());

			// Debug: surface the raw term_info JSON fed into this processor so a
			// mishandled term can be inspected in-panel even if later steps fail.
			// Emitted early and HTML-escaped; always present in debug builds.
			if (debug) {
				String dbg = json.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
				addModelHtml("<pre style=\"white-space:pre-wrap;word-break:break-all\">" + dbg + "</pre>",
						"Debug (raw term_info)", "debug", metaDataType, geppettoModelAccess);
			}

			if (!superTypes.isEmpty()) {
				for (String supertype : superTypes) {
					if (!supertype.startsWith("_")) {
						parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
					}
				}
			} else {
				parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("Orphan", dependenciesLibrary));
			}

			// Name: <b>{label}</b> [{sf}] {types}
			addModelHtml("<b>" + name + "</b> [" + id + "] " + typeString,
					"Name", "label", metaDataType, geppettoModelAccess);

			// Title (pub terms)
			List<Publication> pubs = pubList(ti);
			if (!pubs.isEmpty() && pubs.get(0).title != null && !pubs.get(0).title.isEmpty()
					&& superTypes.contains("pub")) {
				addModelHtml("<b>" + pubs.get(0).title + "</b>", "Title", "title", metaDataType, geppettoModelAccess);
			}

			// Symbol
			String symbol = symbolText(optStr(meta, "Symbol"));
			if (!symbol.isEmpty()) {
				addModelHtml("<b>" + symbol + "</b>", "Symbol", "symbol", metaDataType, geppettoModelAccess);
			}

			// Logo / Link
			addModelHtml(mdToHtml(optStr(meta, "Logo")), "Logo", "logo", metaDataType, geppettoModelAccess);
			addModelHtml(mdToHtml(optStr(meta, "Link")), "Link", "link", metaDataType, geppettoModelAccess);

			// Description (def_pubs already inline) + Comment block, mirroring legacy definition()
			String desc = optStr(meta, "Description");
			String comment = optStr(meta, "Comment");
			if (!desc.isEmpty() || !comment.isEmpty()) {
				StringBuilder d = new StringBuilder();
				if (!desc.isEmpty()) {
					d.append("<span class=\"terminfo-description\">").append(mdToHtml(desc)).append("</span>");
				}
				if (!comment.isEmpty()) {
					d.append("<br /><span class=\"terminfo-comment-title\">Comment</span><br /><span class=\"terminfo-comment\">")
					 .append(mdToHtml(comment)).append("</span>");
				}
				addModelHtml(d.toString(), "Description", "description", metaDataType, geppettoModelAccess);
			}

			// Synonyms -> Alternative Names
			String syn = synonymsHtml(ti);
			addModelHtml(syn, "Alternative Names", "synonyms", metaDataType, geppettoModelAccess);

			// Source + License (from Licenses{})
			List<License> lics = licenseList(ti);
			if (!lics.isEmpty()) {
				StringBuilder src = new StringBuilder();
				StringBuilder lic = new StringBuilder();
				for (License l : lics) {
					if (l.source != null && !l.source.isEmpty()) {
						String s = (l.source_iri != null && !l.source_iri.isEmpty())
								? "<a href=\"?id=" + lastId(l.source_iri) + "\" data-instancepath=\"" + lastId(l.source_iri) + "\">" + l.source + "</a>"
								: l.source;
						if (src.length() > 0) src.append("<br/>");
						src.append(s);
					}
					if (l.label != null && !l.label.isEmpty()) {
						String licHtml = (l.short_form != null && !l.short_form.isEmpty())
								? "<a href=\"?id=" + l.short_form + "\" data-instancepath=\"" + l.short_form + "\">" + l.label + "</a>"
								: l.label;
						if (l.icon != null && !l.icon.isEmpty()) {
							licHtml += " <img class=\"terminfo-licenseicon\" src=\"" + secureUrl(l.icon) + "\" title=\"" + l.label + "\"/>";
						}
						if (lic.length() > 0) lic.append("<br/>");
						lic.append(licHtml);
					}
				}
				boolean isPub = superTypes.contains("pub");
				if (src.length() > 0) {
					addModelHtml("<span class=\"terminfo-source\">" + src.toString() + "</span>", isPub ? "Related DataSets" : "Source", "source", metaDataType, geppettoModelAccess);
				}
				if (lic.length() > 0 && !isPub) {
					addModelHtml("<span class=\"terminfo-license\">" + lic.toString() + "</span>", "License", "license", metaDataType, geppettoModelAccess);
				}
			}

			// Classification (parents) / Relationships / Related Individuals
			addModelHtml(metaListToHtml(optStr(meta, "Types"), "Classification"), "Classification", "type", metaDataType, geppettoModelAccess);
			addModelHtml(relationshipsToHtml(optStr(meta, "Relationships")), "Relationships", "relationships", metaDataType, geppettoModelAccess);
			addModelHtml(metaListToHtml(optStr(meta, "RelatedIndividuals"), "related_individuals"), "Related Individuals", "related_individuals", metaDataType, geppettoModelAccess);

			// Cross References (xrefs)
			String xrefs = xrefsHtml(ti);
			addModelHtml(xrefs, "Cross References", "xrefs", metaDataType, geppettoModelAccess);

			// Images / Examples / Domains -> thumbnails + downloads
			emitImages(ti, variable, parentType, metaDataType, dataSource, geppettoModelAccess, dependenciesLibrary);

			// References
			String refs = referencesHtml(ti, pubs);
			addModelHtml(refs, "References", "references", metaDataType, geppettoModelAccess);

			// Queries (from VFBquery Queries[]) with count badge + grey-out
			emitQueries(ti, tempId, name, metaDataType, geppettoModelAccess);


		} catch (Exception e) {
			System.out.println("Error in VFBProcessTermInfoVFBqueryJson: " + e.toString());
			e.printStackTrace();
		}
		return results;
	}

	// ---- row builders -------------------------------------------------------

	private String synonymsHtml(JsonObject ti) {
		if (!ti.has("Synonyms") || !ti.get("Synonyms").isJsonArray()) {
			return "";
		}
		List<Synonym> syns = new ArrayList<Synonym>();
		Gson g = new Gson();
		for (JsonElement el : ti.getAsJsonArray("Synonyms")) {
			syns.add(g.fromJson(el, Synonym.class));
		}
		if (syns.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("<ul class=\"terminfo-synonyms\">");
		for (Synonym s : syns) {
			if (s == null || s.label == null || s.label.isEmpty()) {
				continue;
			}
			String scope = (s.scope == null) ? "" : s.scope.replace("has_", "").replace("_", " ").trim();
			String line = s.label;
			if (!scope.isEmpty() && !scope.equalsIgnoreCase("exact")) {
				line = scope + ": " + s.label;
			}
			if (s.publication != null && !s.publication.isEmpty()) {
				line += " (" + mdToHtml(s.publication) + ")";
			}
			sb.append("<li>").append(line).append("</li>");
		}
		sb.append("</ul>");
		return sb.length() > "<ul class=\"terminfo-synonyms\"></ul>".length() ? sb.toString() : "";
	}

	private String xrefsHtml(JsonObject ti) {
		if (!ti.has("Xrefs") || !ti.get("Xrefs").isJsonArray()) {
			return "";
		}
		Gson g = new Gson();
		StringBuilder sb = new StringBuilder();
		for (JsonElement el : ti.getAsJsonArray("Xrefs")) {
			Xref x = g.fromJson(el, Xref.class);
			if (x == null || x.link == null || x.link.isEmpty()) {
				continue;
			}
			String label = x.label == null ? "" : x.label;
			String icon = (x.icon != null && !x.icon.isEmpty())
					? "<img class=\"terminfo-siteicon\" src=\"" + secureUrl(x.icon) + "\"/> "
					: "";
			if (sb.length() > 0) sb.append("<br/>");
			sb.append("<a href=\"").append(x.link).append("\" target=\"_blank\" title=\"").append(label).append("\">")
					.append(icon).append(label).append("</a>");
		}
		return sb.toString();
	}

	// References aggregates every publication the term cites: its own Publications
	// (full microref + xref icons), plus the pubs carried on synonyms and inline in
	// the definition (label + link only -- the full FlyBase/DOI/PMID breakdown is not
	// in get_term_info for referenced pubs). De-duplicated by target short_form.
	private String referencesHtml(JsonObject ti, List<Publication> pubs) {
		java.util.LinkedHashMap<String, String> byId = new java.util.LinkedHashMap<String, String>();
		// 1) the term's own Publications -- full entry with xref icons
		if (pubs != null) {
			for (Publication p : pubs) {
				if (p == null) continue;
				String mref = p.microref != null && !p.microref.isEmpty() ? mdToHtml(p.microref)
						: (p.short_form != null ? p.short_form : "");
				if (mref.isEmpty()) continue;
				StringBuilder icons = new StringBuilder();
				if (p.refs != null) {
					for (String r : p.refs) {
						String cls = r.contains("pubmed") ? "gpt-pubmed" : r.contains("doi.org") ? "gpt-doi"
								: r.contains("flybase") ? "gpt-fly" : "fa-external-link";
						icons.append(" <a href=\"").append(r).append("\" target=\"_blank\"><i class=\"popup-icon-link ")
								.append(cls).append("\"></i></a>");
					}
				}
				String key = p.short_form != null && !p.short_form.isEmpty() ? p.short_form : mref;
				byId.put(key, mref + icons.toString());
			}
		}
		// 2) synonym pubs + 3) inline definition pubs -- label + link, only if new
		java.util.List<String> mds = new java.util.ArrayList<String>();
		if (ti.has("Synonyms") && ti.get("Synonyms").isJsonArray()) {
			for (JsonElement el : ti.getAsJsonArray("Synonyms")) {
				if (el.isJsonObject() && el.getAsJsonObject().has("publication") && !el.getAsJsonObject().get("publication").isJsonNull()) {
					mds.add(el.getAsJsonObject().get("publication").getAsString());
				}
			}
		}
		JsonObject meta = ti.has("Meta") && ti.get("Meta").isJsonObject() ? ti.getAsJsonObject("Meta") : null;
		if (meta != null && meta.has("Description") && !meta.get("Description").isJsonNull()) {
			mds.add(meta.get("Description").getAsString());
		}
		for (String md : mds) {
			if (md == null) continue;
			Matcher m = MD_LINK.matcher(md);
			while (m.find()) {
				String id = m.group(2);
				if (id == null || id.isEmpty() || byId.containsKey(id)) continue;
				byId.put(id, "<a href=\"?id=" + id + "\" data-instancepath=\"" + id + "\">" + m.group(1) + "</a>");
			}
		}
		if (byId.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("<ul class=\"terminfo-references\">");
		for (String entry : byId.values()) {
			sb.append("<li>").append(entry).append("</li>");
		}
		sb.append("</ul>");
		return sb.toString();
	}

	// ---- images / visualisation ---------------------------------------------

	private void emitImages(JsonObject ti, Variable variable, CompositeType parentType, CompositeType metaDataType,
			DataSource dataSource, GeppettoModelAccess access, List<GeppettoLibrary> dependenciesLibrary) throws GeppettoVisitingException {
		Gson g = new Gson();
		String varId = variable.getId();
		String varName = variable.getName() != null && !variable.getName().isEmpty() ? variable.getName() : varId;

		// Term's own images: 3D geometry (OBJ/SWC) + slices (WLZ) attach to parentType;
		// thumbnail carousel + downloads + "Aligned to" attach to metaDataType.
		if (ti.has("Images") && ti.get("Images").isJsonObject() && ti.getAsJsonObject("Images").entrySet().size() > 0) {
			JsonObject images = ti.getAsJsonObject("Images");
			ArrayValue thumbs = ValuesFactory.eINSTANCE.createArrayValue();
			int[] tIdx = {0};
			List<String> downloadData = new ArrayList<String>();
			StringBuilder downloadFiles = new StringBuilder();
			List<List<String>> domains = buildDomains(ti);
			// Prefer the loaded template as primary so the attached geometry/carousel
			// is the loaded template's alignment (not an arbitrary first Images key).
			// Detect the loaded template only for multi-template terms; the common
			// single-template case keeps the first key as primary with zero overhead.
			String loadedTemplate = "";
			if (images.entrySet().size() > 1) {
				for (String at : AVAILABLE_TEMPLATES) {
					try {
						if (ModelUtility.getTypeFromLibrary(at + "_metadata", dataSource.getTargetLibrary()) != null) {
							loadedTemplate = at;
							break;
						}
					} catch (Exception ex) {
						/* template not loaded */
					}
				}
			}
			String primaryTemplate = (!loadedTemplate.isEmpty() && images.has(loadedTemplate)) ? loadedTemplate : null;
			java.util.List<String> alignedTemplates = new java.util.ArrayList<String>();
			String bibtexFolder = null;
			boolean geometryLoaded = false;
			for (Map.Entry<String, JsonElement> e : images.entrySet()) {
				String templateSf = e.getKey();
				if (!e.getValue().isJsonArray()) continue;
				if (primaryTemplate == null) primaryTemplate = templateSf;
				if (!alignedTemplates.contains(templateSf)) alignedTemplates.add(templateSf);
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					ImageRec r = g.fromJson(el, ImageRec.class);
					if (r == null) continue;
					// Carousel shows the term's own thumbnail(s) in the primary template space.
					if (templateSf.equals(primaryTemplate)) {
						String thumb = r.thumbnail_transparent != null ? r.thumbnail_transparent : r.thumbnail;
						if (thumb != null && !thumb.isEmpty()) {
							addImage(secureUrl(thumb), r.label != null ? r.label : r.id, r.id, thumbs, tIdx[0]++);
						}
						// 3D geometry + slices: load once for the primary alignment.
						if (!geometryLoaded) {
							if (r.obj != null && r.obj.contains(".obj")) {
								addModelObj(secureUrl(r.obj).replace("https://", "http://"), "3D Volume", varId, parentType, access, dataSource);
								appendDownload(downloadFiles, downloadData, "obj", r.obj, templateSf, varId, varName);
							}
							if (r.swc != null && r.swc.contains(".swc")) {
								addModelSwc(secureUrl(r.swc).replace("https://", "http://"), "3D Skeleton", varId, parentType, access, dataSource);
								appendDownload(downloadFiles, downloadData, "swc", r.swc, templateSf, varId, varName);
							}
							if (r.wlz != null && r.wlz.contains(".wlz")) {
								addModelSlices(secureUrl(r.wlz), "Stack Viewer Slices", varId, parentType, access, dataSource, domains);
								appendDownload(downloadFiles, downloadData, "wlz", r.wlz, templateSf, varId, varName);
							}
							if (r.nrrd != null && r.nrrd.contains(".nrrd")) {
								appendDownload(downloadFiles, downloadData, "nrrd", r.nrrd, templateSf, varId, varName);
							}
							String fldSrc = (r.nrrd != null) ? r.nrrd : (r.obj != null) ? r.obj : r.wlz;
							if (fldSrc != null && fldSrc.lastIndexOf('/') >= 0) {
								bibtexFolder = fldSrc.substring(0, fldSrc.lastIndexOf('/') + 1);
							}
							geometryLoaded = true;
						}
					}
				}
			}
			// List the loaded/primary template first so the (single-template) frontend
			// reads it as the alignment and does not prompt a template change.
			if (primaryTemplate != null && alignedTemplates.remove(primaryTemplate)) {
				alignedTemplates.add(0, primaryTemplate);
			}
			if (!alignedTemplates.isEmpty()) {
				// Every template the term is registered to (the Images keys) becomes a
				// super-type, so the loader can test the loaded template against the
				// complete aligned set rather than a single value; and each is listed in
				// "Aligned to". Primary (first key) stays first, preserving existing
				// single-template behaviour. Template label is not in the per-term payload
				// for other terms, so fall back to the short_form (self = term label).
				StringBuilder tplLinks = new StringBuilder();
				for (String tpl : alignedTemplates) {
					String tplText = tpl.equals(varId) && varName != null && !varName.isEmpty() ? varName
							: (TEMPLATE_NAMES.containsKey(tpl) ? TEMPLATE_NAMES.get(tpl) : tpl);
					if (tplLinks.length() > 0) tplLinks.append(", ");
					tplLinks.append("<a href=\"?id=").append(tpl).append("\" data-instancepath=\"").append(tpl).append("\">").append(tplText).append("</a>");
					parentType.getSuperType().add(access.getOrCreateSimpleType(tpl, dependenciesLibrary));
				}
				addModelHtml(tplLinks.toString(), "Aligned to", "template", metaDataType, access);
			}
			if (!thumbs.getElements().isEmpty()) {
				addModelThumbnails(thumbs, "Thumbnail", "thumbnail", metaDataType, access);
			}
			if (downloadFiles.length() > 0) {
				if (bibtexFolder != null) {
					String bibHref = bibtexFolder.replace("http://", "https://").replace("https://www.virtualflybrain.org/data/", "/data/") + "citations.bibtex";
					downloadFiles.append("<br>Remember to cite: <a download=\"").append(varId)
						.append(".bibtex\" href=\"").append(bibHref).append("\">citations.bibtex</a>");
					downloadFiles.append("<br>The license shown above applies to this data.");
				} else {
					downloadFiles.append("<br>Note: see source &amp; license above for terms of reuse and correct attribution.");
				}
				addModelHtml(downloadFiles.toString(), "Downloads", "downloads", metaDataType, access);
				addModelFileMeta(downloadData, "DownloadMeta", "filemeta", metaDataType, access);
			}
		}

		// Examples (classes): thumbnail carousel + hasExamples flag, no geometry.
		if (ti.has("Examples") && ti.get("Examples").isJsonObject() && ti.getAsJsonObject("Examples").entrySet().size() > 0) {
			JsonObject examples = ti.getAsJsonObject("Examples");
			ArrayValue exThumbs = ValuesFactory.eINSTANCE.createArrayValue();
			int[] eIdx = {0};
			for (Map.Entry<String, JsonElement> e : examples.entrySet()) {
				if (!e.getValue().isJsonArray()) continue;
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					ImageRec r = g.fromJson(el, ImageRec.class);
					if (r == null) continue;
					String thumb = r.thumbnail_transparent != null ? r.thumbnail_transparent : r.thumbnail;
					if (thumb != null && !thumb.isEmpty()) {
						addImage(secureUrl(thumb), r.label != null ? r.label : r.id, r.id, exThumbs, eIdx[0]++);
					}
				}
			}
			if (!exThumbs.getElements().isEmpty()) {
				addModelThumbnails(exThumbs, "Available Images", "examples", metaDataType, access);
				parentType.getSuperType().add(access.getOrCreateSimpleType("hasExamples", dependenciesLibrary));
			}
		}
	}

	// Build the WLZ stack-viewer domain arrays (voxel size + per-domain id/name/type/centre),
	// mirroring VFBProcessTermInfoCachedJson.getDomains().
	private List<List<String>> buildDomains(JsonObject ti) {
		List<List<String>> domains = new ArrayList<List<String>>();
		Gson g = new Gson();
		boolean isTemplate = ti.has("IsTemplate") && !ti.get("IsTemplate").isJsonNull() && ti.get("IsTemplate").getAsBoolean();
		if (isTemplate && ti.has("Domains") && ti.get("Domains").isJsonObject() && ti.getAsJsonObject("Domains").entrySet().size() > 0) {
			String[] domainId = new String[600];
			String[] domainName = new String[600];
			String[] domainType = new String[600];
			String[] domainCentre = new String[600];
			String[] voxelSize = new String[]{null, null, null, null};
			if (ti.has("Images") && ti.get("Images").isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : ti.getAsJsonObject("Images").entrySet()) {
					if (e.getValue().isJsonArray() && e.getValue().getAsJsonArray().size() > 0) {
						ImageRec self = g.fromJson(e.getValue().getAsJsonArray().get(0), ImageRec.class);
						if (self != null) {
							if (self.voxel != null) {
								voxelSize[0] = String.valueOf(self.voxel.X);
								voxelSize[1] = String.valueOf(self.voxel.Y);
								voxelSize[2] = String.valueOf(self.voxel.Z);
							}
							// Index 0 is the template itself; its centre lives on the self
							// image record (Domains["0"].center is null). The slice viewer's
							// callDstRange joins this centre, so it must not be null.
							if (self.center != null && self.center.X != null && self.center.Y != null && self.center.Z != null) {
								domainCentre[0] = "[" + self.center.X.intValue() + ", " + self.center.Y.intValue() + ", " + self.center.Z.intValue() + "]";
							}
						}
						break;
					}
				}
			}
			for (Map.Entry<String, JsonElement> e : ti.getAsJsonObject("Domains").entrySet()) {
				int i;
				try { i = Integer.parseInt(e.getKey()); } catch (NumberFormatException ex) { continue; }
				if (i < 0 || i >= 600 || !e.getValue().isJsonObject()) continue;
				JsonObject d = e.getValue().getAsJsonObject();
				domainId[i] = d.has("id") && !d.get("id").isJsonNull() ? d.get("id").getAsString() : null;
				domainName[i] = d.has("type_label") && !d.get("type_label").isJsonNull() ? d.get("type_label").getAsString() : null;
				domainType[i] = d.has("type_id") && !d.get("type_id").isJsonNull() ? d.get("type_id").getAsString() : null;
				if (d.has("center") && d.get("center").isJsonObject()) {
					JsonObject c = d.getAsJsonObject("center");
					if (c.has("X") && c.has("Y") && c.has("Z") && !c.get("Z").isJsonNull()) {
						domainCentre[i] = "[" + c.get("X").getAsInt() + ", " + c.get("Y").getAsInt() + ", " + c.get("Z").getAsInt() + "]";
					}
				}
			}
			domains.add(Arrays.asList(voxelSize));
			domains.add(Arrays.asList(domainId));
			domains.add(Arrays.asList(domainName));
			domains.add(Arrays.asList(domainType));
			domains.add(Arrays.asList(domainCentre));
		} else {
			String sf = optStr(ti, "Id");
			String label = optStr(ti, "Name");
			if (label.isEmpty()) label = sf;
			domains.add(Arrays.asList(new String[]{"0.622088", "0.622088", "0.622088", null}));
			domains.add(Arrays.asList(new String[]{sf}));
			domains.add(Arrays.asList(new String[]{label}));
			domains.add(Arrays.asList(new String[]{sf}));
			domains.add(Arrays.asList(new String[]{"[511, 255, 108]"}));
		}
		return domains;
	}

	// Append one download entry (HTML link + filemeta JSON) for a format, mirroring the legacy processor.
	private void appendDownload(StringBuilder html, List<String> data, String fmt, String url, String template, String varId, String varName) {
		if (url == null || url.isEmpty()) return;
		String https = url.replace("http://", "https://");
		String href = https.replace("https://www.virtualflybrain.org/data/", "/data/");
		String v2 = https.replace("https://www.virtualflybrain.org/data/", "https://v2.virtualflybrain.org/data/");
		String safeName = varName.replace(" ", "_");
		String label;
		String folder;
		String ext;
		if ("obj".equals(fmt)) {
			boolean pcl = url.contains("volume.obj");
			label = pcl ? "Pointcloud (OBJ)" : "Mesh (OBJ)";
			folder = pcl ? "PointCloudFiles(OBJ)" : "MeshFiles(OBJ)";
			ext = "obj";
		} else if ("swc".equals(fmt)) {
			label = "Skeleton (SWC)"; folder = "Skeleton(SWC)"; ext = "swc";
		} else if ("wlz".equals(fmt)) {
			label = "Slices (Woolz)"; folder = "Slices(WOOLZ)"; ext = "wlz";
		} else {
			label = "Signal (NRRD)"; folder = "SignalFiles(NRRD)"; ext = "nrrd";
		}
		String fname = "obj".equals(fmt) ? varId + (url.contains("volume.obj") ? "_pointCloud.obj" : "_mesh.obj") : varId + "." + ext;
		html.append("<br>").append(label).append(": <a download=\"").append(fname)
			.append("\" href=\"").append(href).append("\">").append(fname).append("</a>");
		data.add("'" + fmt + "':{'url':'" + v2 + "','local':'" + template + "/" + folder + "/" + varId + "_(" + safeName + ")." + ext + "'}");
	}

	// ---- 3D / slice model loaders (ported verbatim from VFBProcessTermInfoCachedJson) ----

	private void addModelObj(String url, String name, String reference, CompositeType parentType, GeppettoModelAccess geppettoModelAccess, DataSource dataSource) {
		try {
			if (url == null || url.equals("")) {
				return;
			}
			Variable Variable = VariablesFactory.eINSTANCE.createVariable();
			ImportType importType = TypesFactory.eINSTANCE.createImportType();
			importType.setUrl(url);
			importType.setId(reference + "_obj");
			importType.setModelInterpreterId("objModelInterpreterService");
			Variable.getTypes().add(importType);
			Variable.setId(reference + "_obj");
			Variable.setName("3D Volume");
			geppettoModelAccess.addVariableToType(Variable, parentType);
			geppettoModelAccess.addTypeToLibrary(importType, getLibraryFor(dataSource, "obj"));
		} catch (Exception e) {
			System.out.println("Error adding OBJ to model (" + reference + ") " + e.toString());
			e.printStackTrace();
		}
	}

	private void addModelSwc(String url, String name, String reference, CompositeType parentType, GeppettoModelAccess geppettoModelAccess, DataSource dataSource) {
		try {
			if (url == null || url.equals("")) {
				return;
			}
			Variable Variable = VariablesFactory.eINSTANCE.createVariable();
			ImportType importType = TypesFactory.eINSTANCE.createImportType();
			importType.setUrl(url);
			importType.setId(reference + "_swc");
			importType.setModelInterpreterId("swcModelInterpreter");
			Variable.getTypes().add(importType);
			Variable.setId(reference + "_swc");
			Variable.setName("3D Skeleton");
			geppettoModelAccess.addVariableToType(Variable, parentType);
			geppettoModelAccess.addTypeToLibrary(importType, getLibraryFor(dataSource, "swc"));
		} catch (Exception e) {
			System.out.println("Error adding SWC to model (" + reference + ") " + e.toString());
			e.printStackTrace();
		}
	}

	private void addModelSlices(String url, String name, String reference, CompositeType parentType, GeppettoModelAccess geppettoModelAccess, DataSource dataSource, List<List<String>> domains) throws GeppettoVisitingException {
		try {
			if (url == null || url.equals("")) {
				return;
			}
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
			Variable slicesVar = VariablesFactory.eINSTANCE.createVariable();
			Image slicesValue = ValuesFactory.eINSTANCE.createImage();
			slicesValue.setData(new Gson().toJson(new IIPJSON(0, "https://www.virtualflybrain.org/fcgi/wlziipsrv.fcgi", url.replace("https://", "http://").replace("www.virtualflybrain.org", "virtualflybrain.org").replace("http://virtualflybrain.org/data/", "/disk/data/VFB/IMAGE_DATA/").replace("http://virtualflybrain.org/private/", "/disk/data/VFB/IMAGE_PRIVATE/"), domains)));
			slicesValue.setFormat(ImageFormat.IIP);
			slicesValue.setReference(reference);
			slicesVar.setId(reference + "_slices");
			slicesVar.setName("Stack Viewer Slices");
			slicesVar.getTypes().add(imageType);
			slicesVar.getInitialValues().put(imageType, slicesValue);
			geppettoModelAccess.addVariableToType(slicesVar, parentType);
		} catch (Exception e) {
			System.out.println("Error adding slices:");
			e.printStackTrace();
		}
	}

	private GeppettoLibrary getLibraryFor(DataSource dataSource, String format) {
		for (DataSourceLibraryConfiguration lc : dataSource.getLibraryConfigurations()) {
			if (lc.getFormat().equals(format)) {
				return lc.getLibrary();
			}
		}
		System.out.println(format + " Not Found!");
		return null;
	}

	private class IIPJSON {
		int indexNumber;
		String serverUrl;
		String fileLocation;
		List<List<String>> subDomains;

		public IIPJSON(int indexNumber, String serverUrl, String fileLocation, List<List<String>> subDomains) {
			this.indexNumber = indexNumber;
			this.fileLocation = fileLocation;
			this.serverUrl = serverUrl;
			this.subDomains = subDomains;
		}
	}

	private void emitQueries(JsonObject ti, String varId, String name, CompositeType metaDataType,
			GeppettoModelAccess access) throws GeppettoVisitingException {
		if (!ti.has("Queries") || !ti.get("Queries").isJsonArray()) {
			return;
		}
		Gson g = new Gson();
		// Inline pill style so the result count reads clearly as a badge, not text,
		// regardless of whether the frontend ships terminfo-count-badge CSS.
		final String pill = "display:inline-block;min-width:0.9em;padding:1px 6px;margin-right:6px;"
				+ "border-radius:9px;font-size:0.72em;font-weight:bold;line-height:1.5;text-align:center;"
				+ "vertical-align:middle;color:#ffffff;";
		List<String> rows = new ArrayList<String>();
		for (JsonElement el : ti.getAsJsonArray("Queries")) {
			Query q = g.fromJson(el, Query.class);
			if (q == null || q.query == null) continue;
			long count = q.count == null ? -1 : q.count.longValue();
			String badge;
			String cssExtra = "";
			if (count == 0) {
				badge = "<span class=\"terminfo-count-badge terminfo-count-empty\" style=\"" + pill
						+ "background-color:#9b9b9b;\" title=\"0 results\">0</span>";
				cssExtra = " terminfo-query-empty";
			} else if (count > 0) {
				badge = "<span class=\"terminfo-count-badge\" style=\"" + pill
						+ "background-color:#428bca;\" title=\"" + count + " results\">" + formatCount(count) + "</span>";
			} else {
				badge = "<i class=\"popup-icon-link fa fa-quora\"></i>";
			}
			String label = q.label != null ? q.label : q.query;
			String href = "/org.geppetto.frontend/geppetto?q=" + varId + "," + q.query;
			rows.add("<div class=\"terminfo-query" + cssExtra + "\">" + badge
					+ "<a href=\"" + href + "\" data-instancepath=\"" + q.query + "," + varId + "," + name + "\">"
					+ label + "</a></div>");
		}
		if (!rows.isEmpty()) {
			addModelHtml(String.join("", rows), "Query for", "queries", metaDataType, access);
		}
	}

	// ---- small JSON helpers -------------------------------------------------

	private static String optStr(JsonObject o, String key) {
		if (o != null && o.has(key) && !o.get(key).isJsonNull() && o.get(key).isJsonPrimitive()) {
			return o.get(key).getAsString();
		}
		return "";
	}

	private static List<String> strList(JsonObject o, String key) {
		List<String> out = new ArrayList<String>();
		if (o != null && o.has(key) && o.get(key).isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray(key)) {
				if (e.isJsonPrimitive()) out.add(e.getAsString());
			}
		}
		return out;
	}

	private List<License> licenseList(JsonObject ti) {
		List<License> out = new ArrayList<License>();
		if (ti.has("Licenses") && ti.get("Licenses").isJsonObject()) {
			Gson g = new Gson();
			for (Map.Entry<String, JsonElement> e : ti.getAsJsonObject("Licenses").entrySet()) {
				out.add(g.fromJson(e.getValue(), License.class));
			}
		}
		return out;
	}

	private List<Publication> pubList(JsonObject ti) {
		List<Publication> out = new ArrayList<Publication>();
		if (ti.has("Publications") && ti.get("Publications").isJsonArray()) {
			Gson g = new Gson();
			for (JsonElement e : ti.getAsJsonArray("Publications")) {
				out.add(g.fromJson(e, Publication.class));
			}
		}
		return out;
	}

	private static String symbolText(String md) {
		if (md == null || md.isEmpty()) return "";
		Matcher m = MD_LINK.matcher(md);
		return m.find() ? m.group(1) : md;
	}

	/** De-link markdown to plain text, replacing every [label](id) with its label
	 *  but KEEPING any surrounding text. Unlike symbolText (which returns only the
	 *  first link's label), this preserves e.g. a leading confidence "84% " prefix
	 *  on a relationship. */
	private static String stripLinks(String md) {
		if (md == null || md.isEmpty()) return "";
		Matcher m = MD_LINK.matcher(md);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1)));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String lastId(String iri) {
		if (iri == null) return "";
		String s = iri;
		int slash = s.lastIndexOf('/');
		if (slash >= 0) s = s.substring(slash + 1);
		return s;
	}

	private static String typesString(List<String> tags) {
		// Mirror the legacy returnType(): wrap each tag in a label span and the
		// whole set in a "label types" span (prepended, so order is reversed to
		// match v2). The outer span MUST always be emitted even when there are no
		// tags, because the Term Context list renderer
		// (listViewerConfiguration.js) does htmlLabels.match(/<span>/).join() on
		// the Name-row HTML and NPEs if no span is present.
		String result = "";
		if (tags != null) {
			for (String t : tags) {
				if (t == null || t.isEmpty()) continue;
				result = "<span class=\"label label-" + t + "\">" + t.replace("_", " ") + "</span> " + result;
			}
		}
		return "<span class=\"label types\">" + result + "</span>";
	}

	// ---- model helpers (mirrors of VFBProcessTermInfoCachedJson) ------------

	private void addModelHtml(String data, String name, String reference, CompositeType metaDataType,
			GeppettoModelAccess geppettoModelAccess) throws GeppettoVisitingException {
		if (data == null || data.equals("")) {
			return;
		}
		Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
		Variable label = VariablesFactory.eINSTANCE.createVariable();
		label.setId(reference);
		label.setName(name);
		label.getTypes().add(htmlType);
		HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
		label.getInitialValues().put(htmlType, labelValue);
		labelValue.setHtml(data);
		geppettoModelAccess.addVariableToType(label, metaDataType);
	}

	private void addModelString(String data, String name, String reference, CompositeType metaDataType,
			GeppettoModelAccess geppettoModelAccess) throws GeppettoVisitingException {
		if (data == null || data.equals("")) {
			return;
		}
		Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
		Variable label = VariablesFactory.eINSTANCE.createVariable();
		label.setId(reference);
		label.setName(name);
		label.getTypes().add(textType);
		Text labelValue = ValuesFactory.eINSTANCE.createText();
		label.getInitialValues().put(textType, labelValue);
		labelValue.setText(data);
		geppettoModelAccess.addVariableToType(label, metaDataType);
	}

	private void addModelFileMeta(List<String> data, String name, String reference, CompositeType metaDataType,
			GeppettoModelAccess geppettoModelAccess) throws GeppettoVisitingException {
		String result = "{";
		for (String file : data) {
			if (!result.equals("{")) {
				result += ",";
			}
			result += file;
		}
		result += "}";
		addModelString(result, name, reference, metaDataType, geppettoModelAccess);
	}

	private void addModelThumbnails(ArrayValue images, String name, String reference, CompositeType metaDataType,
			GeppettoModelAccess geppettoModelAccess) throws GeppettoVisitingException {
		Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
		Variable imageVariable = VariablesFactory.eINSTANCE.createVariable();
		imageVariable.setId(reference);
		imageVariable.setName(name);
		imageVariable.getTypes().add(imageType);
		geppettoModelAccess.addVariableToType(imageVariable, metaDataType);
		if (images.getElements().size() > 1) {
			imageVariable.getInitialValues().put(imageType, images);
		} else if (!images.getElements().isEmpty()) {
			imageVariable.getInitialValues().put(imageType, images.getElements().get(0).getInitialValue());
		}
	}

	private void addImage(String data, String name, String reference, ArrayValue images, int i) {
		Image image = ValuesFactory.eINSTANCE.createImage();
		image.setName(name);
		image.setData(secureUrl(data));
		image.setReference(reference);
		image.setFormat(ImageFormat.PNG);
		ArrayElement element = ValuesFactory.eINSTANCE.createArrayElement();
		element.setIndex(i);
		element.setInitialValue(image);
		images.getElements().add(element);
	}
}
