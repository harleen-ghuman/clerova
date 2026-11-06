/**
 * True when every word of the search is somewhere in the fields, ignoring
 * case, "#" and extra spaces: "crest 882" finds Crestline's load #88213.
 */
export function matchesSearch(query: string, fields: (string | null | undefined)[]): boolean {
  const words = query.toLowerCase().replace(/#/g, " ").split(/\s+/).filter(Boolean);

  if (words.length === 0) {
    return true;
  }

  const text = fields.filter(Boolean).join(" ").toLowerCase();
  return words.every((word) => text.includes(word));
}
