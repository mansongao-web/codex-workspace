#!/usr/bin/env node
import { promises as fs } from "node:fs";
import path from "node:path";
import os from "node:os";

const sourceDir = process.argv[2];
const targetDir = process.argv[3] || path.join(os.homedir(), ".codex", "agents");

if (!sourceDir) {
  console.error("Usage: node install-agency-agents-local.mjs <agency-agents-dir> [target-agents-dir]");
  process.exit(2);
}

const ignoredDirs = new Set([
  ".git",
  ".github",
  "integrations",
  "scripts",
  "node_modules",
]);

const ignoredFiles = new Set([
  "README.md",
  "CHANGELOG.md",
  "CONTRIBUTING.md",
  "LICENSE.md",
  "LICENSE",
]);

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function tomlString(value) {
  return JSON.stringify(String(value).replace(/\r\n/g, "\n"));
}

function parseAgentMarkdown(filePath, text) {
  const frontmatter = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  let body = text;
  const meta = {};

  if (frontmatter) {
    body = text.slice(frontmatter[0].length);
    for (const line of frontmatter[1].split(/\r?\n/)) {
      const match = line.match(/^([A-Za-z0-9_-]+):\s*(.*)$/);
      if (!match) continue;
      const key = match[1].trim();
      let value = match[2].trim();
      value = value.replace(/^["']|["']$/g, "");
      meta[key] = value;
    }
  }

  const baseName = path.basename(filePath, ".md");
  const firstHeading = body.match(/^#\s+(.+)$/m)?.[1]?.trim();
  const name = meta.name || firstHeading || baseName;
  const description =
    meta.description ||
    meta.summary ||
    body.match(/^>\s*(.+)$/m)?.[1]?.trim() ||
    `${name} agent from agency-agents.`;

  return {
    name,
    description,
    instructions: body.trim(),
  };
}

async function walk(dir, out = []) {
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!ignoredDirs.has(entry.name)) {
        await walk(path.join(dir, entry.name), out);
      }
      continue;
    }

    if (!entry.isFile()) continue;
    if (!entry.name.toLowerCase().endsWith(".md")) continue;
    if (ignoredFiles.has(entry.name)) continue;
    out.push(path.join(dir, entry.name));
  }

  return out;
}

async function main() {
  const root = path.resolve(sourceDir);
  const stat = await fs.stat(root).catch(() => null);
  if (!stat?.isDirectory()) {
    throw new Error(`Source directory does not exist: ${root}`);
  }

  const markdownFiles = await walk(root);
  if (markdownFiles.length === 0) {
    throw new Error(`No agent markdown files found under: ${root}`);
  }

  await fs.mkdir(targetDir, { recursive: true });
  let installed = 0;
  const seen = new Map();

  for (const file of markdownFiles.sort()) {
    const text = await fs.readFile(file, "utf8");
    const agent = parseAgentMarkdown(file, text);
    if (!agent.instructions) continue;

    let slug = slugify(path.basename(file, ".md"));
    if (!slug) slug = slugify(agent.name);
    const originalSlug = slug;
    let suffix = 2;
    while (seen.has(slug)) {
      slug = `${originalSlug}-${suffix++}`;
    }
    seen.set(slug, file);

    const toml = [
      `name = ${tomlString(agent.name)}`,
      `description = ${tomlString(agent.description)}`,
      `developer_instructions = ${tomlString(agent.instructions)}`,
      "",
    ].join("\n");

    await fs.writeFile(path.join(targetDir, `${slug}.toml`), toml, "utf8");
    installed += 1;
  }

  console.log(JSON.stringify({ source: root, target: targetDir, installed }, null, 2));
}

main().catch((error) => {
  console.error(error.stack || String(error));
  process.exit(1);
});
