import assert from "node:assert/strict";
import test from "node:test";
import {
  fromDraft,
  imagePath,
  publishChecklist,
  publishErrors,
  safeLink,
} from "../src/lib/content.ts";
import type { Draft } from "../src/types.ts";
import { reactive } from "vue";

test("external links reject credentials and active protocols", () => {
  for (const raw of [
    "javascript:alert(1)",
    "data:text/html,x",
    "//evil.example",
    "https://user:secret@example.com",
  ])
    assert.equal(safeLink(raw), "");
  assert.equal(
    safeLink("https://example.com/docs?q=a"),
    "https://example.com/docs?q=a",
  );
});
test("private images only recognize exact media path without query token", () => {
  const path = "/api/v1/media/images/01234567-89ab-cdef-0123-456789abcdef";
  assert.equal(imagePath(path), path);
  assert.equal(imagePath(`${path}?token=x`), "");
  assert.equal(imagePath(`https://example.com${path}`), "");
});
test("complex Markdown and source links survive editing projection verbatim", () => {
  const body =
    "# 标题\n\n```cpp\n<script>literal</script>\n```\n\n|列|值|\n|-|-|\n|甲|乙|\n\n![图][p]\n[p]: /api/v1/media/images/01234567-89ab-cdef-0123-456789abcdef";
  const value: Draft = {
    articleId: 1,
    revision: 4,
    state: "DRAFT",
    content: {
      title: "测试",
      summary: "",
      body,
      tags: { jobIds: [1], majorIds: [11], companyIds: [], topicIds: [] },
      experienceStage: "大三",
      sourceType: "DEMO",
      sourceNote: "",
      sourceLinks: [{ label: "资料", url: "https://example.com" }],
    },
    images: [],
    referencedImageIds: [],
  };
  const form = fromDraft(value);
  assert.equal(form.body, body);
  form.jobIds.push(2);
  assert.deepEqual(value.content.tags.jobIds, [1]);
  assert.deepEqual(publishErrors(form), []);
  assert.equal(fromDraft(reactive(value)).body, body);
  form.sourceType = "CURATED";
  assert.ok(publishErrors(form).includes("补充来源说明"));
});

test("publish checklist maps server-only body validation to the editor without duplicates", () => {
  const form = {
    title: "",
    summary: "",
    body: "##",
    jobIds: [1],
    majorIds: [11],
    companyIds: [],
    topicIds: [],
    experienceStage: "大三",
    sourceType: "CURATED",
    sourceNote: "",
    sourceLinks: [],
  };
  assert.deepEqual(
    publishChecklist(form, [
      "title",
      "body或有效上传图片",
      "sourceNote",
      "sourceNote",
    ]),
    [
      { field: "title", label: "填写标题" },
      { field: "body", label: "添加正文或已上传图片" },
      { field: "sourceNote", label: "补充来源说明" },
    ],
  );
});
