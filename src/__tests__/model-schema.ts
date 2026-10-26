import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPOSITORY = fileURLToPath(new URL("../../", import.meta.url));

/**
 * Where `weivel/model`'s schema is checked out: named by `MODEL_SCHEMA`, else inside this repository
 * where CI places the sibling, else beside it where a developer keeps it.
 *
 * @implNote A path that is NAMED is held to it rather than falling back, so a CI pointing at the
 * wrong place fails instead of quietly reading a stale local copy. Engine's PublishedSchemaTest
 * resolves the same three places in the same order.
 */
export function modelSchemaPath(): string {
  const named = process.env.MODEL_SCHEMA;
  const candidates = named
    ? [resolve(REPOSITORY, named)]
    : [resolve(REPOSITORY, "model/plugin.schema.json"), resolve(REPOSITORY, "../model/plugin.schema.json")];
  const found = candidates.find((candidate) => existsSync(candidate));
  if (!found) {
    throw new Error(
      `plugin.schema.json is read from weivel/model, checked out beside this repository; not found at ${candidates.join(" or ")}`,
    );
  }
  return found;
}

const LOCAL_DEFINITION = "#/definitions/";

type Definitions = Record<string, unknown>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function definitionReferenced(node: Record<string, unknown>, definitions: Definitions): unknown {
  if (typeof node.$ref !== "string" || !node.$ref.startsWith(LOCAL_DEFINITION)) return undefined;
  return definitions[node.$ref.slice(LOCAL_DEFINITION.length)];
}

function inlineDefinitions(node: unknown, definitions: Definitions): unknown {
  if (Array.isArray(node)) return node.map((element) => inlineDefinitions(element, definitions));
  if (!isRecord(node)) return node;
  const referenced = definitionReferenced(node, definitions);
  if (referenced !== undefined) return inlineDefinitions(referenced, definitions);
  return Object.fromEntries(Object.entries(node).map(([key, value]) => [key, inlineDefinitions(value, definitions)]));
}

/**
 * @returns the manifest vocabulary as `weivel/model` publishes it, with each local
 * `#/definitions/<name>` reference replaced by the definition it names and `definitions` dropped
 *
 * @implNote The model names a subschema as a definition so a library declaration can `$ref` it, which
 * changes where it is written and not what it validates. The bundle builds the same tree inline, so
 * the comparison is between what the two validate; a reference to anything else is left in place and
 * fails the comparison visibly. Engine's PublishedSchemaTest inlines the same way.
 */
export function readModelSchema(): unknown {
  const { definitions = {}, ...schema } = JSON.parse(readFileSync(modelSchemaPath(), "utf8")) as Record<string, unknown>;
  return inlineDefinitions(schema, definitions as Definitions);
}
