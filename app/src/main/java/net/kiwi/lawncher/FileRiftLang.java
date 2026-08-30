package net.kiwi.lawncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight FileRift language support ported from the VS Code extension
 * (analyze.js + block_formats). Outline, completions, template index —
 * no full LSP process; runs in-process against the editor buffer.
 */
final class FileRiftLang {
	private FileRiftLang() {}

	static final Pattern BLOCK_OPEN = Pattern.compile("^(\\w+)\\s*\\{");
	static final Pattern FIELD_COLON = Pattern.compile("^(\\w+)\\s*:\\s*(.*)$");
	static final Pattern FIELD_NOCOLON = Pattern.compile("^(\\w+)(?:\\s+(\\S.*))?$");
	static final Pattern TEMPLATE_REF = Pattern.compile("(\\w*Template\\w*)\\s*:?\\s*['\"]([^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);

	/** Outline / nav node. */
	static final class Node {
		final String type; // Object, Component, ObjectLibrary, Bounds, Template
		final int line;    // 0-based
		String id = "";
		String cls = "";
		int idLine = -1;
		boolean isTemplateDef;
		final List<Node> kids = new ArrayList<>();

		Node(String type, int line) {
			this.type = type;
			this.line = line;
		}

		String label() {
			if ("Component".equals(type)) {
				String c = cls.isEmpty() ? "Component" : cls;
				return id.isEmpty() ? c : c + " · " + id;
			}
			if (id.isEmpty()) return type;
			return type + " · " + id;
		}
	}

	static final class Diagnostic {
		final int line; // 0-based
		final int startCol;
		final int length;
		final String message;
		Diagnostic(int line, int startCol, int length, String message) {
			this.line = line;
			this.startCol = startCol;
			this.length = length;
			this.message = message;
		}
	}

	/**
	 * A $ ... $end embedded Lua (or other) chunk.
	 * openLine = line with "Key: $", endLine = line with "$end",
	 * body is the text between them (exclusive of markers).
	 */
	static final class Chunk {
		final String key;
		/** Mutable so collapsed view can remap lines to placeholder rows. */
		int openLine;
		int endLine;
		final int index; // 0-based order in file
		String body;

		Chunk(String key, int openLine, int endLine, int index, String body) {
			this.key = key;
			this.openLine = openLine;
			this.endLine = endLine;
			this.index = index;
			this.body = body == null ? "" : body;
		}

		String label() {
			String k = key == null || key.isEmpty() ? "chunk" : key;
			int lines = Math.max(0, endLine - openLine - 1);
			return k + "  $" + "  (" + lines + " lines)";
		}

		String tabTitle() {
			String k = key == null || key.isEmpty() ? "chunk" : key;
			return k + ".lua";
		}
	}

	static final class Analysis {
		final List<Node> outline = new ArrayList<>();
		final Map<String, Integer> templates = new LinkedHashMap<>(); // name -> line
		final List<String> imports = new ArrayList<>();
		final List<Diagnostic> diagnostics = new ArrayList<>();
		final List<Chunk> chunks = new ArrayList<>();
		final String[] scopePerLine;
		final boolean[] inChunk;
		String[] lines;

		Analysis(int n) {
			scopePerLine = new String[n];
			inChunk = new boolean[n];
		}
	}

	static final class Field {
		final String key;
		final String value;
		Field(String k, String v) { key = k; value = v; }
	}

	static String stripQuotes(String s) {
		if (s == null) return "";
		s = s.trim();
		if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))
			return s.substring(1, s.length() - 1);
		return s.replaceAll(",\\s*$", "").trim();
	}

	static Field parseFieldLine(String raw) {
		String trimmed = raw.trim();
		if (trimmed.isEmpty() || trimmed.equals("}") || trimmed.startsWith("#")) return null;
		if (BLOCK_OPEN.matcher(trimmed).find()) return null;
		int colon = trimmed.indexOf(':');
		if (colon != -1) {
			String key = trimmed.substring(0, colon).trim();
			String value = trimmed.substring(colon + 1).trim().replaceAll(",\\s*$", "");
			if (key.isEmpty()) return null;
			return new Field(key, value);
		}
		Matcher m = FIELD_NOCOLON.matcher(trimmed);
		if (!m.matches()) return null;
		return new Field(m.group(1), m.group(2) == null ? "" : m.group(2).replaceAll(",\\s*$", "").trim());
	}

	static Analysis analyze(String text, String rootExt) {
		String[] lines = text.split("\n", -1);
		int n = lines.length;
		Analysis a = new Analysis(n);
		List<String> scopeStack = new ArrayList<>();
		scopeStack.add(rootExt == null || rootExt.isEmpty() ? "scene" : rootExt);
		List<String> tagStack = new ArrayList<>();
		List<Node> navStack = new ArrayList<>();
		boolean inChunk = false;
		int chunkOpen = -1;
		String chunkKey = "";
		StringBuilder chunkBody = null;

		for (int i = 0; i < n; i++) {
			String rawLine = lines[i] == null ? "" : lines[i];
			String line = rawLine.trim();
			a.scopePerLine[i] = scopeStack.get(scopeStack.size() - 1);

			if (inChunk) {
				if (line.equals("$end")) {
					a.inChunk[i] = false;
					inChunk = false;
					String body = chunkBody == null ? "" : chunkBody.toString();
					// strip trailing newline we added between body lines
					if (body.endsWith("\n")) body = body.substring(0, body.length() - 1);
					a.chunks.add(new Chunk(chunkKey, chunkOpen, i, a.chunks.size(), body));
					chunkBody = null;
				} else {
					a.inChunk[i] = true;
					if (chunkBody.length() > 0) chunkBody.append('\n');
					chunkBody.append(rawLine);
				}
				continue;
			}
			// "Key: $" or "Key $" opens a chunk
			if (line.matches("^\\w+\\s*:?\\s*\\$\\s*$")) {
				inChunk = true;
				a.inChunk[i] = true; // marker line itself
				chunkOpen = i;
				chunkKey = line.replaceAll("\\s*:?\\s*\\$\\s*$", "").trim();
				chunkBody = new StringBuilder();
				continue;
			}

			Matcher open = BLOCK_OPEN.matcher(line);
			if (open.find()) {
				String tag = open.group(1);
				String parent = scopeStack.get(scopeStack.size() - 1);
				String childScope = childScopeFor(parent, tag);
				scopeStack.add(childScope);

				Node node = null;
				if ("Object".equals(tag)) {
					boolean isTpl = !tagStack.isEmpty() && "Template".equals(tagStack.get(tagStack.size() - 1));
					node = new Node("Object", i);
					node.isTemplateDef = isTpl;
					a.outline.add(node);
				} else if ("ObjectLibrary".equals(tag)) {
					node = new Node("ObjectLibrary", i);
					a.outline.add(node);
				} else if ("Bounds".equals(tag)) {
					node = new Node("Bounds", i);
					a.outline.add(node);
				} else if ("Template".equals(tag)) {
					node = new Node("Template", i);
					a.outline.add(node);
				} else if ("Component".equals(tag)) {
					node = new Node("Component", i);
					for (int j = navStack.size() - 1; j >= 0; j--) {
						Node p = navStack.get(j);
						if (p != null && "Object".equals(p.type)) {
							p.kids.add(node);
							break;
						}
					}
				}
				navStack.add(node);
				tagStack.add(tag);
				continue;
			}

			if (line.equals("}")) {
				if (scopeStack.size() > 1) scopeStack.remove(scopeStack.size() - 1);
				if (!navStack.isEmpty()) navStack.remove(navStack.size() - 1);
				if (!tagStack.isEmpty()) tagStack.remove(tagStack.size() - 1);
				continue;
			}

			Field field = parseFieldLine(line);
			if (field == null) continue;
			String val = stripQuotes(field.value);
			if ("ImportedLibrary".equals(field.key)) a.imports.add(val);

			Node top = navStack.isEmpty() ? null : navStack.get(navStack.size() - 1);
			if (top != null) {
				if ("Object".equals(top.type) && "Identifier".equals(field.key)) {
					top.id = val;
					top.idLine = i;
				} else if ("ObjectLibrary".equals(top.type) && "Name".equals(field.key)) {
					top.id = val;
				} else if ("Template".equals(top.type) && ("Name".equals(field.key) || "Identifier".equals(field.key))) {
					top.id = val;
					top.idLine = i;
				} else if ("Component".equals(top.type)) {
					if ("ClassName".equals(field.key)) top.cls = val;
					else if ("Identifier".equals(field.key)) top.id = val;
				}
			}
		}

		for (Node node : a.outline) {
			if (node.isTemplateDef && node.id != null && !node.id.isEmpty()) {
				a.templates.put(node.id, node.idLine >= 0 ? node.idLine : node.line);
			}
			if ("Template".equals(node.type) && node.id != null && !node.id.isEmpty()) {
				a.templates.put(node.id, node.idLine >= 0 ? node.idLine : node.line);
			}
		}
		a.lines = lines;
		return a;
	}

	/** Unknown tags/fields under current scope + unresolved local template refs. */
	static void computeDiagnostics(Analysis a, String[] lines) {
		a.diagnostics.clear();
		int n = lines.length;
		for (int i = 0; i < n; i++) {
			if (a.inChunk[i]) continue;
			String raw = lines[i];
			String trimmed = raw == null ? "" : raw.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("}")) continue;

			String scope = a.scopePerLine[i] != null ? a.scopePerLine[i] : "scene";
			List<String> allowed = fieldsForScope(scope);
			// Also allow structural children known for this scope
			Matcher open = BLOCK_OPEN.matcher(trimmed);
			if (open.find()) {
				String tag = open.group(1);
				if (!allowed.contains(tag) && !isKnownStructural(tag)) {
					int col = raw.indexOf(tag);
					if (col < 0) col = 0;
					a.diagnostics.add(new Diagnostic(i, col, tag.length(),
							"\"" + tag + "\" is not defined under [" + scope + "]"));
				}
				continue;
			}

			Field field = parseFieldLine(raw);
			if (field == null) continue;
			if ("Comment".equals(field.key)) continue;
			if (!allowed.contains(field.key) && !isKnownStructural(field.key)) {
				int col = raw.indexOf(field.key);
				if (col < 0) col = 0;
				a.diagnostics.add(new Diagnostic(i, col, field.key.length(),
						"Field \"" + field.key + "\" is not defined under [" + scope + "]"));
			}

			Matcher tr = TEMPLATE_REF.matcher(trimmed);
			if (tr.find() && tr.group(2) != null && !tr.group(2).isEmpty()) {
				String name = tr.group(2);
				if (!a.templates.containsKey(name)) {
					int col = raw.indexOf(name);
					if (col < 0) col = 0;
					a.diagnostics.add(new Diagnostic(i, col, name.length(),
							"Template \"" + name + "\" not found in this file"));
				}
			}
		}
	}

	static boolean isKnownStructural(String tag) {
		return "Object".equals(tag) || "Component".equals(tag) || "Template".equals(tag)
				|| "ObjectLibrary".equals(tag) || "Bounds".equals(tag) || "Group".equals(tag)
				|| "OnLoad".equals(tag);
	}

	/** Line of template definition, or -1. */
	static int findTemplateLine(Analysis a, String name) {
		if (a == null || name == null) return -1;
		Integer line = a.templates.get(name);
		return line == null ? -1 : line;
	}

	/** Template name under cursor line, or null. */
	static String templateRefAtLine(String line) {
		if (line == null) return null;
		Matcher tr = TEMPLATE_REF.matcher(line.trim());
		if (tr.find()) return tr.group(2);
		return null;
	}

	static String childScopeFor(String parent, String tag) {
		Map<String, String> m = SCOPE_CHILD.get(parent);
		if (m != null && m.containsKey(tag)) return m.get(tag);
		if ("Object".equals(tag)) return "SceneObject";
		if ("Component".equals(tag)) return "Component";
		if ("Template".equals(tag)) return "ObjectTemplate";
		if ("ObjectLibrary".equals(tag)) return "ObjectLibrary";
		if ("Bounds".equals(tag)) return "SceneBounds";
		return tag;
	}

	/** Completions for the current line / scope. */
	static List<String> completions(Analysis a, String text, int cursor) {
		String[] lines = text.split("\n", -1);
		int lineIdx = 0, col = cursor;
		for (int i = 0; i < lines.length; i++) {
			int len = lines[i].length() + (i < lines.length - 1 ? 1 : 0);
			if (col <= lines[i].length()) { lineIdx = i; break; }
			col -= len;
			lineIdx = i;
		}
		if (lineIdx >= a.scopePerLine.length) lineIdx = a.scopePerLine.length - 1;
		if (lineIdx < 0) return Collections.emptyList();
		if (a.inChunk[lineIdx]) return Collections.emptyList();

		String scope = a.scopePerLine[lineIdx];
		String line = lines[lineIdx];
		String before = line.substring(0, Math.min(col, line.length()));
		String prefix = before.replaceAll("^\\s+", "");
		// If typing a field value after colon, suggest templates when key looks like *Template*
		int colon = prefix.indexOf(':');
		if (colon >= 0) {
			String key = prefix.substring(0, colon).trim();
			if (key.toLowerCase(Locale.ROOT).contains("template")) {
				List<String> out = new ArrayList<>();
				String q = stripQuotes(prefix.substring(colon + 1).trim()).toLowerCase(Locale.ROOT);
				for (String name : a.templates.keySet()) {
					if (q.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(q))
						out.add("'" + name + "'");
				}
				return out;
			}
			return Collections.emptyList();
		}

		List<String> keys = new ArrayList<>(fieldsForScope(scope));
		String q = prefix.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String k : keys) {
			if (q.isEmpty() || k.toLowerCase(Locale.ROOT).startsWith(q)) out.add(k);
		}
		// Also structural tags at file root
		if (q.isEmpty() || "object".startsWith(q)) out.add("Object");
		if (q.isEmpty() || "component".startsWith(q)) out.add("Component");
		if (q.isEmpty() || "template".startsWith(q)) out.add("Template");
		Collections.sort(out);
		// de-dupe
		LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
		for (String s : out) seen.put(s, true);
		return new ArrayList<>(seen.keySet());
	}


	/** Replace the body of a chunk (between openLine and endLine markers) with newBody. */
	static String spliceChunk(String text, Chunk chunk, String newBody) {
		if (text == null || chunk == null) return text;
		String[] lines = text.split("\n", -1);
		if (chunk.openLine < 0 || chunk.endLine <= chunk.openLine || chunk.endLine >= lines.length)
			return text;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i <= chunk.openLine; i++) {
			if (i > 0) sb.append('\n');
			sb.append(lines[i]);
		}
		if (newBody != null && !newBody.isEmpty()) {
			sb.append('\n');
			sb.append(newBody);
		}
		sb.append('\n');
		sb.append(lines[chunk.endLine]);
		for (int i = chunk.endLine + 1; i < lines.length; i++) {
			sb.append('\n');
			sb.append(lines[i]);
		}
		return sb.toString();
	}

	/** Find chunk whose openLine matches, or by key+index. */
	static Chunk findChunk(Analysis a, int openLine) {
		if (a == null) return null;
		for (Chunk c : a.chunks) {
			if (c.openLine == openLine) return c;
		}
		return null;
	}


	/**
	 * Collapsed chunk shown as a single structural block:
	 *   OnLoad { … }
	 * instead of Key: $ / body / $end. Darker styling is applied in the editor.
	 */
	static String chunkPlaceholder(Chunk c) {
		String k = c.key == null || c.key.isEmpty() ? "chunk" : c.key;
		return "\t" + k + " { … }";
	}

	static boolean isChunkPlaceholder(String line) {
		if (line == null) return false;
		String t = line.trim();
		// OnLoad { … }  or  Program { ... }
		return t.matches("^\\w+\\s*\\{\\s*(…|\\.\\.\\.)\\s*\\}\\s*$");
	}

	/** Key embedded in a placeholder line, or null. */
	static String placeholderKey(String line) {
		if (!isChunkPlaceholder(line)) return null;
		String t = line.trim();
		int brace = t.indexOf('{');
		if (brace <= 0) return null;
		return t.substring(0, brace).trim();
	}

	/**
	 * Replace each full $…$end chunk (open marker through $end) with one
	 * "Key { … }" line so the parent buffer reads like normal structure.
	 */
	static String collapseChunks(String text, Analysis a) {
		if (text == null || a == null || a.chunks.isEmpty()) return text;
		String[] lines = text.split("\n", -1);
		for (int ci = a.chunks.size() - 1; ci >= 0; ci--) {
			Chunk c = a.chunks.get(ci);
			if (c.openLine < 0 || c.endLine < c.openLine || c.endLine >= lines.length) continue;
			// Preserve indent from the open line if present
			String open = lines[c.openLine];
			String indent = "";
			for (int i = 0; i < open.length(); i++) {
				char ch = open.charAt(i);
				if (ch == ' ' || ch == '\t') indent += ch;
				else break;
			}
			String placeholder = indent + (c.key == null || c.key.isEmpty() ? "chunk" : c.key) + " { … }";
			List<String> next = new ArrayList<>();
			for (int i = 0; i < c.openLine; i++) next.add(lines[i]);
			next.add(placeholder);
			for (int i = c.endLine + 1; i < lines.length; i++) next.add(lines[i]);
			lines = next.toArray(new String[0]);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) sb.append('\n');
			sb.append(lines[i]);
		}
		return sb.toString();
	}

	/**
	 * Expand "Key { … }" placeholders back to Key: $\n body \n $end
	 * using bodies stored on Analysis.chunks.
	 */
	static String expandChunks(String collapsed, Analysis a) {
		if (collapsed == null || a == null || a.chunks.isEmpty()) return collapsed;
		String[] lines = collapsed.split("\n", -1);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (isChunkPlaceholder(line)) {
				String key = placeholderKey(line);
				Chunk c = null;
				if (key != null) {
					for (Chunk cand : a.chunks) {
						if (key.equals(cand.key)) { c = cand; break; }
					}
				}
				String indent = "";
				for (int j = 0; j < line.length(); j++) {
					char ch = line.charAt(j);
					if (ch == ' ' || ch == '\t') indent += ch;
					else break;
				}
				String k = (c != null && c.key != null && !c.key.isEmpty())
						? c.key : (key != null ? key : "chunk");
				out.add(indent + k + ": $");
				if (c != null && c.body != null && !c.body.isEmpty()) {
					String[] bodyLines = c.body.split("\n", -1);
					for (String bl : bodyLines) out.add(bl);
				}
				out.add(indent + "$end");
			} else {
				out.add(line);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < out.size(); i++) {
			if (i > 0) sb.append('\n');
			sb.append(out.get(i));
		}
		return sb.toString();
	}

	static List<String> fieldsForScope(String scope) {
		List<String> f = SCOPE_FIELDS.get(scope);
		if (f != null) return f;
		return SCOPE_FIELDS.getOrDefault("SceneObject", COMMON_FIELDS);
	}

	static String rootExtFromFilename(String name) {
		if (name == null) return "scene";
		String lower = name.toLowerCase(Locale.ROOT);
		for (String ext : new String[]{"scene", "scl", "gopt", "gplayer", "gdata", "gstate", "scmap", "fr"}) {
			if (lower.endsWith("." + ext)) return ext;
		}
		return "scene";
	}

	// ── Format tables (subset of blockFormatsData.js) ───────────────────

	private static final List<String> COMMON_FIELDS = Arrays.asList(
			"Identifier", "Name", "ClassName", "Template", "TemplateName",
			"Position", "Depth", "Rotation", "Scaling", "Hidden", "OnLoad",
			"Component", "Object", "ImportedLibrary", "Texture", "Program"
	);

	private static final Map<String, List<String>> SCOPE_FIELDS = new HashMap<>();
	private static final Map<String, Map<String, String>> SCOPE_CHILD = new HashMap<>();

	static {
		SCOPE_FIELDS.put("scene", Arrays.asList(
				"Name", "Template", "ImportedLibrary", "Texture", "Program",
				"Object", "ObjectLibrary", "Bounds", "Group", "OnLoad",
				"Item", "Skill", "Quest"
		));
		SCOPE_FIELDS.put("scl", SCOPE_FIELDS.get("scene"));
		SCOPE_FIELDS.put("fr", SCOPE_FIELDS.get("scene"));
		SCOPE_FIELDS.put("gdata", Arrays.asList(
				"Name", "Item", "Skill", "Quest", "Object", "Template", "ImportedLibrary"
		));
		SCOPE_FIELDS.put("gopt", Arrays.asList(
				"Name", "Identifier", "Value", "Type", "Object", "Component"
		));
		SCOPE_FIELDS.put("gplayer", Arrays.asList(
				"Name", "Identifier", "Position", "Health", "Object", "Component", "Inventory"
		));
		SCOPE_FIELDS.put("SceneObject", Arrays.asList(
				"Identifier", "TemplateName", "Template", "Name", "Position", "Depth",
				"Rotation", "Scaling", "LocalAabb", "Hidden", "OnLoad", "Component",
				"Object", "ImportedLibrary", "Texture", "Program", "CanBecomeActive",
				"Locked", "EntityClass", "GuideTarget", "Type", "Top", "Left", "Right",
				"Bottom", "ObjectIdentifier", "Item", "Skill", "Quest", "Title",
				"ShortDescription", "Description", "Unique", "MinDamage", "MaxDamage"
		));
		SCOPE_FIELDS.put("Component", Arrays.asList(
				"ClassName", "Identifier", "Name", "Enabled", "OnLoad", "Value",
				"Target", "Speed", "Duration", "Trigger", "Message", "Position",
				"Rotation", "Scaling", "Hidden", "Texture", "Program", "Type"
		));
		SCOPE_FIELDS.put("ObjectTemplate", Arrays.asList(
				"Name", "Identifier", "Object", "Component", "ImportedLibrary"
		));
		SCOPE_FIELDS.put("ObjectLibrary", Arrays.asList(
				"Name", "Object", "Template", "ImportedLibrary"
		));
		SCOPE_FIELDS.put("SceneBounds", Arrays.asList(
				"Top", "Left", "Right", "Bottom", "Identifier"
		));

		Map<String, String> sceneChild = new HashMap<>();
		sceneChild.put("Object", "SceneObject");
		sceneChild.put("ObjectLibrary", "ObjectLibrary");
		sceneChild.put("Bounds", "SceneBounds");
		sceneChild.put("Template", "ObjectTemplate");
		sceneChild.put("Group", "SceneObject");
		SCOPE_CHILD.put("scene", sceneChild);
		SCOPE_CHILD.put("scl", sceneChild);
		SCOPE_CHILD.put("fr", sceneChild);
		SCOPE_CHILD.put("gdata", sceneChild);

		Map<String, String> objChild = new HashMap<>();
		objChild.put("Component", "Component");
		objChild.put("Object", "SceneObject");
		SCOPE_CHILD.put("SceneObject", objChild);
		SCOPE_CHILD.put("ObjectTemplate", objChild);
	}
}
