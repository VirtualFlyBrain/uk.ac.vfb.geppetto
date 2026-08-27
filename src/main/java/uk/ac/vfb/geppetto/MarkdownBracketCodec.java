package uk.ac.vfb.geppetto;

/**
 * Reverses VFBquery's {@code encode_brackets()} percent-encoding of literal
 * square brackets ({@code "%5B"} -&gt; {@code "["}, {@code "%5D"} -&gt;
 * {@code "]"}) inside markdown-link label text.
 *
 * <p>VFBquery renders every term label as markdown, {@code "[label](id)"}.
 * FlyBase-style allele/transgene names commonly contain literal square
 * brackets (e.g. {@code "P{GawB}how[24B]"}, {@code "elav[C155]-GAL4"}), so
 * VFBquery percent-encodes any {@code "["} / {@code "]"} INSIDE the label
 * first -- otherwise an unescaped {@code "]"} would terminate the markdown
 * link early (see {@code encode_brackets} / {@code _encode_regular_md_link}
 * in {@code vfb_queries.py}). That encoding exists only to survive the
 * markdown round-trip: once a label has been pulled back out of its
 * {@code "[label](id)"} wrapper for display, the percent-codes must be
 * decoded back to literal brackets, or the user sees e.g.
 * {@code "P{GawB}how%5B24B%5D"} instead of {@code "P{GawB}how[24B]"}
 * (confirmed live on FBti0150063 -- expression pattern names, relationship
 * targets, synonyms, xrefs, licenses and query-result image alt text all
 * go through this same label path).
 *
 * <p><strong>Where to apply:</strong> on every label captured out of a
 * markdown-link regex (VFBProcessTermInfoVFBqueryJson's {@code MD_LINK}, or
 * VFBqueryJsonProcessor's {@code IMAGE_MARKDOWN} alt group) at the point it
 * becomes final display text -- never on a URL/target/href group, and never
 * on text that is left markdown-intact for something downstream (e.g. the
 * frontend's MarkdownLinkComponent) to parse itself, since decoding a
 * bracket before that parse would just reopen the original truncation bug
 * one layer later.
 */
final class MarkdownBracketCodec {

	private MarkdownBracketCodec() {
	}

	/**
	 * Decode {@code %5B}/{@code %5b} -&gt; {@code [} and {@code %5D}/{@code %5d}
	 * -&gt; {@code ]}. Safe to call on already-clean text (cheap no-op guard on
	 * the presence of {@code '%'}) and on {@code null}/empty input.
	 */
	static String decodeBrackets(String text) {
		if (text == null || text.isEmpty() || text.indexOf('%') < 0) {
			return text;
		}
		return text.replace("%5B", "[").replace("%5b", "[")
				.replace("%5D", "]").replace("%5d", "]");
	}
}
