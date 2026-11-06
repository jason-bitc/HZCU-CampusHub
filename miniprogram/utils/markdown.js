function normalizeExternalUrl(value) {
  const url = String(value || '').trim();
  // Reject control characters, backslashes and credentials; text is never HTML.
  return /^https?:\/\/[^\s\\<>"\u0000-\u001f\u007f]+$/i.test(url) && !/^https?:\/\/[^/]*@/i.test(url) ? url : '';
}

function resolveAssetUrl(value, assetBaseUrl) {
  const url = String(value || '').trim();
  const absolute = normalizeExternalUrl(url);
  if (absolute) return absolute;
  if (url.charAt(0) === '/' && !url.startsWith('//') && assetBaseUrl) {
    return normalizeExternalUrl(`${String(assetBaseUrl).replace(/\/+$/, '')}${url}`);
  }
  return '';
}

function parseInline(value) {
  const text = String(value || '');
  const segments = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g;
  let cursor = 0;
  let match;
  while ((match = pattern.exec(text))) {
    if (match.index > cursor) segments.push({ type: 'text', text: text.slice(cursor, match.index) });
    const token = match[0];
    if (token.startsWith('**')) {
      segments.push({ type: 'strong', text: token.slice(2, -2) });
    } else if (token.startsWith('`')) {
      segments.push({ type: 'code', text: token.slice(1, -1) });
    } else {
      const parts = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
      const url = parts ? normalizeExternalUrl(parts[2].split(/\s+/)[0]) : '';
      if (url) segments.push({ type: 'link', text: parts[1], url });
      else segments.push({ type: 'text', text: token });
    }
    cursor = match.index + token.length;
  }
  if (cursor < text.length) segments.push({ type: 'text', text: text.slice(cursor) });
  const result = segments.length ? segments : [{ type: 'text', text: '' }];
  return result.map((segment, key) => Object.assign({ key }, segment));
}

function parseMarkdown(body, options) {
  const settings = options || {};
  const lines = String(body || '').replace(/\r\n?/g, '\n').split('\n');
  const nodes = [];
  const headings = [];
  const images = [];
  let paragraph = [];
  let index = 0;

  function nextId() {
    index += 1;
    return index;
  }

  function flushParagraph() {
    if (!paragraph.length) return;
    const text = paragraph.join('\n').trim();
    paragraph = [];
    if (!text) return;
    nodes.push({ id: nextId(), type: 'paragraph', segments: parseInline(text) });
  }

  for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
    const line = lines[lineIndex];
    const trimmed = line.trim();
    if (!trimmed) {
      flushParagraph();
      continue;
    }

    const fence = /^(`{3,}|~{3,})\s*([\w+-]*)[^\n]*$/.exec(trimmed);
    if (fence) {
      flushParagraph();
      const codeLines = [];
      const language = fence[2] || '';
      const closeFence = new RegExp(`^${fence[1].charAt(0)}{${fence[1].length},}\\s*$`);
      lineIndex += 1;
      while (lineIndex < lines.length && !closeFence.test(lines[lineIndex].trim())) {
        codeLines.push(lines[lineIndex]);
        lineIndex += 1;
      }
      nodes.push({ id: nextId(), type: 'code', language, text: codeLines.join('\n') });
      continue;
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(trimmed);
    if (heading) {
      flushParagraph();
      const id = nextId();
      const node = { id, type: 'heading', level: heading[1].length, text: heading[2].replace(/\s+#+\s*$/, '').trim() };
      nodes.push(node);
      headings.push({ id, level: node.level, text: node.text, indent: Math.max(0, node.level - 2) * 16 });
      continue;
    }

    const imagePattern = /!\[([^\]]*)\]\((\S+?)(?:\s+["'][^"']*["'])?\)/g;
    const image = imagePattern.exec(line);
    if (image) {
      flushParagraph();
      let cursor = 0;
      let match = image;
      while (match) {
        if (match.index > cursor) {
          paragraph.push(line.slice(cursor, match.index));
          flushParagraph();
        }
        const src = resolveAssetUrl(match[2], settings.assetBaseUrl);
        nodes.push({ id: nextId(), type: 'image', alt: match[1] || '正文图片', src, originalSrc: src, failed: !src });
        if (src) images.push(src);
        cursor = match.index + match[0].length;
        match = imagePattern.exec(line);
      }
      if (cursor < line.length) paragraph.push(line.slice(cursor));
      continue;
    }

    if (/^>\s?/.test(trimmed)) {
      flushParagraph();
      const quoteLines = [];
      while (lineIndex < lines.length && /^>\s?/.test(lines[lineIndex].trim())) {
        quoteLines.push(lines[lineIndex].trim().replace(/^>\s?/, ''));
        lineIndex += 1;
      }
      lineIndex -= 1;
      nodes.push({ id: nextId(), type: 'quote', segments: parseInline(quoteLines.join('\n')) });
      continue;
    }

    if (/^([-*+]\s+|\d+[.)]\s+)/.test(trimmed)) {
      flushParagraph();
      const items = [];
      const itemPattern = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/;
      const indentStack = [];
      while (lineIndex < lines.length) {
        const item = itemPattern.exec(lines[lineIndex]);
        if (item) {
          const indent = item[1].replace(/\t/g, '    ').length;
          while (indentStack.length && indent < indentStack[indentStack.length - 1]) indentStack.pop();
          if (!indentStack.length || indent > indentStack[indentStack.length - 1]) indentStack.push(indent);
          const level = indentStack.length - 1;
          items.push({ id: items.length, marker: /^\d/.test(item[2]) ? item[2] : '•', level, indent: Math.min(level, 5) * 24, segments: parseInline(item[3]) });
        } else if (/^\s+\S/.test(lines[lineIndex]) && items.length) {
          const segments = items[items.length - 1].segments;
          parseInline(`\n${lines[lineIndex].trim()}`).forEach((segment) => segments.push(Object.assign({}, segment, { key: segments.length })));
        } else break;
        lineIndex += 1;
      }
      lineIndex -= 1;
      nodes.push({ id: nextId(), type: 'list', ordered: /^\d/.test(items[0].marker), items });
      continue;
    }

    paragraph.push(line);
  }
  flushParagraph();
  return { nodes, headings, images };
}

module.exports = { parseMarkdown, parseInline, normalizeExternalUrl, resolveAssetUrl };
