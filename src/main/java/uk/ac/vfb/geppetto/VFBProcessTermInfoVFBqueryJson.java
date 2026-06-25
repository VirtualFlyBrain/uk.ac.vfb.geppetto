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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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

	private Boolean debug = false;

	private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
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
			// target may be "TEMPLATE,SF" for image cells; take the last id for linking.
			String sf = target.contains(",") ? target.substring(target.lastIndexOf(',') + 1) : target;
			String repl = "<a href=\"?id=" + sf + "\" data-instancepath=\"" + sf + "\">"
					+ Matcher.quoteReplacement(label) + "</a>";
			m.appendReplacement(sb, Matcher.quoteReplacement(repl));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** Convert a VFBquery Meta.* string ("[rel](id): [a](id), [b](id); ...")
	 *  to the old list HTML: <ul class="terminfo-CSS"><li>...</li></ul>. */
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
		if (n < 1000) {
			return Long.toString(n);
		}
		final String[] units = {"K", "M", "B"};
		double value = n;
		int unit = -1;
		while (value >= 1000 && unit < units.length - 1) {
			value /= 1000.0;
			unit++;
		}
		String out;
		if (value < 10) {
			double floored = Math.floor(value * 10) / 10.0; // one decimal, floored
			out = String.format("%.1f", floored);
			if (out.endsWith(".0")) {
				out = out.substring(0, out.length() - 2);
			}
		} else {
			out = Long.toString((long) Math.floor(value));
		}
		return out + units[unit];
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
		Long count;              // result count (may be null / -1 for deferred)
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
			Gson gson = new GsonBuilder().create();
			JsonObject ti = gson.fromJson(json, JsonObject.class);
			if (ti == null) {
				return results;
			}

			String tempId = variable.getId();
			List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();

			JsonObject meta = ti.has("Meta") && ti.get("Meta").isJsonObject() ? ti.getAsJsonObject("Meta") : new JsonObject();
			String name = optStr(ti, "Name");
			String id = optStr(ti, "Id");
			List<String> superTypes = strList(ti, "SuperTypes");
			String typeString = typesString(superTypes);
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
			addModelHtml("<b>" + name + "</b> [" + id + "]" + (typeString.isEmpty() ? "" : " " + typeString),
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

			// Description (def_pubs already inline)
			String desc = optStr(meta, "Description");
			if (!desc.isEmpty()) {
				addModelHtml("<span class=\"terminfo-description\">" + mdToHtml(desc) + "</span>",
						"Description", "description", metaDataType, geppettoModelAccess);
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
						String licHtml = l.label;
						if (l.icon != null && !l.icon.isEmpty()) {
							licHtml += " <img class=\"terminfo-licenseicon\" src=\"" + secureUrl(l.icon) + "\" title=\"" + l.label + "\"/>";
						}
						if (lic.length() > 0) lic.append("<br/>");
						lic.append(licHtml);
					}
				}
				boolean isPub = superTypes.contains("pub");
				if (src.length() > 0) {
					addModelHtml(src.toString(), isPub ? "Related DataSets" : "Source", "source", metaDataType, geppettoModelAccess);
				}
				if (lic.length() > 0 && !isPub) {
					addModelHtml(lic.toString(), "License", "license", metaDataType, geppettoModelAccess);
				}
			}

			// Classification (parents) / Relationships / Related Individuals
			addModelHtml(metaListToHtml(optStr(meta, "Types"), "Classification"), "Classification", "type", metaDataType, geppettoModelAccess);
			addModelHtml(metaListToHtml(optStr(meta, "Relationships"), "relationships"), "Relationships", "relationships", metaDataType, geppettoModelAccess);
			addModelHtml(metaListToHtml(optStr(meta, "RelatedIndividuals"), "related_individuals"), "Related Individuals", "related_individuals", metaDataType, geppettoModelAccess);

			// Cross References (xrefs)
			String xrefs = xrefsHtml(ti);
			addModelHtml(xrefs, "Cross References", "xrefs", metaDataType, geppettoModelAccess);

			// Images / Examples / Domains -> thumbnails + downloads
			emitImages(ti, metaDataType, geppettoModelAccess);

			// References
			String refs = referencesHtml(pubs);
			addModelHtml(refs, "References", "references", metaDataType, geppettoModelAccess);

			// Queries (from VFBquery Queries[]) with count badge + grey-out
			emitQueries(ti, tempId, name, metaDataType, geppettoModelAccess);

			if (debug) {
				addModelHtml(json, "Debug", "debug", metaDataType, geppettoModelAccess);
			}

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
					? "<img class=\"popup-icon-link\" src=\"" + secureUrl(x.icon) + "\"/> "
					: "";
			if (sb.length() > 0) sb.append("<br/>");
			sb.append("<a href=\"").append(x.link).append("\" target=\"_blank\" title=\"").append(label).append("\">")
					.append(icon).append(label).append("</a>");
		}
		return sb.toString();
	}

	private String referencesHtml(List<Publication> pubs) {
		if (pubs == null || pubs.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("<ul class=\"terminfo-references\">");
		boolean any = false;
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
			sb.append("<li>").append(mref).append(icons).append("</li>");
			any = true;
		}
		sb.append("</ul>");
		return any ? sb.toString() : "";
	}

	private void emitImages(JsonObject ti, CompositeType metaDataType, GeppettoModelAccess access) throws GeppettoVisitingException {
		// Examples (anatomy images) then Images (channel/template) — build a
		// thumbnail carousel + a downloads list from the record formats.
		ArrayValue thumbs = ValuesFactory.eINSTANCE.createArrayValue();
		List<String> downloads = new ArrayList<String>();
		int[] idx = {0};
		String[] downloadHtml = {""};
		collectImages(ti, "Examples", thumbs, downloads, downloadHtml, idx);
		collectImages(ti, "Images", thumbs, downloads, downloadHtml, idx);
		if (!thumbs.getElements().isEmpty()) {
			addModelThumbnails(thumbs, "Available Images", "examples", metaDataType, access);
		}
		if (!downloadHtml[0].isEmpty()) {
			addModelHtml(downloadHtml[0], "Downloads", "downloads", metaDataType, access);
			addModelFileMeta(downloads, "DownloadMeta", "filemeta", metaDataType, access);
		}
	}

	private void collectImages(JsonObject ti, String key, ArrayValue thumbs, List<String> downloads,
			String[] downloadHtml, int[] idx) {
		if (!ti.has(key) || !ti.get(key).isJsonObject()) {
			return;
		}
		Gson g = new Gson();
		JsonObject byTemplate = ti.getAsJsonObject(key);
		for (Map.Entry<String, JsonElement> e : byTemplate.entrySet()) {
			if (!e.getValue().isJsonArray()) continue;
			for (JsonElement el : e.getValue().getAsJsonArray()) {
				ImageRec r = g.fromJson(el, ImageRec.class);
				if (r == null || r.thumbnail == null) continue;
				String thumb = r.thumbnail_transparent != null ? r.thumbnail_transparent : r.thumbnail;
				addImage(thumb, r.label != null ? r.label : r.id, r.id, thumbs, idx[0]++);
				StringBuilder dl = new StringBuilder();
				if (r.nrrd != null) dl.append("\"nrrd\":\"").append(secureUrl(r.nrrd)).append("\",");
				if (r.obj != null) dl.append("\"obj\":\"").append(secureUrl(r.obj)).append("\",");
				if (r.wlz != null) dl.append("\"wlz\":\"").append(secureUrl(r.wlz)).append("\",");
				if (r.swc != null) dl.append("\"swc\":\"").append(secureUrl(r.swc)).append("\",");
				if (dl.length() > 0) {
					downloads.add("\"" + r.id + "\":{" + dl.substring(0, dl.length() - 1) + "}");
					if (r.obj != null) {
						downloadHtml[0] += "<a href=\"" + secureUrl(r.obj) + "\" download>Mesh (OBJ)</a> ";
					}
					if (r.nrrd != null) {
						downloadHtml[0] += "<a href=\"" + secureUrl(r.nrrd) + "\" download>Signal (NRRD)</a> ";
					}
				}
			}
		}
	}

	private void emitQueries(JsonObject ti, String varId, String name, CompositeType metaDataType,
			GeppettoModelAccess access) throws GeppettoVisitingException {
		if (!ti.has("Queries") || !ti.get("Queries").isJsonArray()) {
			return;
		}
		Gson g = new Gson();
		List<String> rows = new ArrayList<String>();
		for (JsonElement el : ti.getAsJsonArray("Queries")) {
			Query q = g.fromJson(el, Query.class);
			if (q == null || q.query == null) continue;
			long count = q.count == null ? -1 : q.count;
			String badge;
			String cssExtra = "";
			if (count == 0) {
				badge = "<span class=\"terminfo-count-badge terminfo-count-empty\">0</span>";
				cssExtra = " terminfo-query-empty";
			} else if (count > 0) {
				badge = "<span class=\"terminfo-count-badge\">" + formatCount(count) + "</span>";
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

	private static String lastId(String iri) {
		if (iri == null) return "";
		String s = iri;
		int slash = s.lastIndexOf('/');
		if (slash >= 0) s = s.substring(slash + 1);
		return s;
	}

	private static String typesString(List<String> superTypes) {
		// Mirror the old types() rendering loosely: space-joined readable types,
		// excluding structural markers.
		StringBuilder sb = new StringBuilder();
		for (String t : superTypes) {
			if (t.equals("Entity") || t.equals("Class") || t.equals("Individual") || t.startsWith("has_")) {
				continue;
			}
			if (sb.length() > 0) sb.append(" ");
			sb.append(t.replace("_", " "));
		}
		return sb.toString();
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
