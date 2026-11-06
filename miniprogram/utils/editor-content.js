function formFromDraft(draft) {
  const c = draft.content; const tags = c.tags;
  return { title: c.title, summary: c.summary, body: c.body, jobIds: tags.jobIds.slice(), majorIds: tags.majorIds.slice(), companyIds: tags.companyIds.slice(), topicIds: tags.topicIds.slice(), experienceStage: c.experienceStage, sourceType: c.sourceType, sourceNote: c.sourceNote, sourceLinks: c.sourceLinks.map((link) => ({ label: link.label, url: link.url })) };
}
function blocks(body) {
  // Complex Markdown stays verbatim in the source editor; never flatten its AST.
  if (/(^|\n)(?:\s*(```|~~~|>|[-*+]\s|\d+[.)]\s)|\s*\|)|!\[[^\]]*\]\[|^\s*\[[^\]]+\]:/m.test(body)) return null;
  return body.split(/\n{2,}/).filter((raw) => raw.length).map((raw, index) => ({ index, raw, caption: raw.length > 90 ? raw.slice(0, 90) + '…' : raw, kind: /^!\[[^\]]*\]\([^\n]+\)$/.test(raw.trim()) ? '图片' : '文字' }));
}
function moveBlock(body, index, delta) { const parts = blocks(body); if (!parts || !parts[index] || !parts[index + delta]) return body; [parts[index], parts[index + delta]] = [parts[index + delta], parts[index]]; return parts.map((part) => part.raw).join('\n\n'); }
function removeBlock(body, index) { const parts = blocks(body); return parts && parts[index] ? parts.filter((_, i) => i !== index).map((part) => part.raw).join('\n\n') : body; }
function publishErrors(form) {
  const errors = [];
  if (!form.title.trim()) errors.push('填写标题');
  if (!form.body.trim()) errors.push('添加正文或已上传的图片');
  if (!form.jobIds.length) errors.push('选择岗位');
  if (!form.majorIds.length) errors.push('选择文章适用专业');
  if (!form.experienceStage.trim()) errors.push('填写经历发生时的阶段');
  if (!['FIRST_HAND', 'CURATED', 'DEMO'].includes(form.sourceType)) errors.push('确认内容来源');
  if (form.sourceType === 'CURATED' && !form.sourceNote.trim()) errors.push('补充整理内容的来源说明');
  return errors;
}
function describe(form, catalog) {
  const names = (field, group) => form[field].map((id) => { const found = catalog && catalog[group].find((item) => item.id === id); return found ? found.name : `已停用标签 ${id}`; }).join('、') || '未选择';
  const sources = { FIRST_HAND: '亲历分享', CURATED: '资料整理', DEMO: '演示内容' };
  return [{ label: '标题', value: form.title || '未填写' }, { label: '摘要', value: form.summary || '未填写' }, { label: '岗位', value: names('jobIds', 'jobs') }, { label: '适用专业', value: names('majorIds', 'majors') }, { label: '公司', value: names('companyIds', 'companies') }, { label: '主题', value: names('topicIds', 'topics') }, { label: '经历阶段', value: form.experienceStage || '未填写' }, { label: '来源', value: sources[form.sourceType] || '未选择' }, { label: '来源说明', value: form.sourceNote || '未填写' }, { label: '资料链接', value: form.sourceLinks.map((r) => `${r.label}\n${r.url}`).join('\n\n') || '未添加' }, { label: '正文', value: form.body || '未填写' }];
}
module.exports = { formFromDraft, blocks, moveBlock, removeBlock, publishErrors, describe };
