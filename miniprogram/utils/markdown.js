function normalizeExternalUrl(value) {
  const url = String(value || '').trim();
  return /^https?:\/\/[^\s]+$/i.test(url) ? url : '';
}

function resolveAssetUrl(value, assetBaseUrl) {
  const url = String(value || '').trim();
  if (/^https?:\/\/[^\s]+$/i.test(url)) return url;
  if (url.charAt(0) === '/' && !url.startsWith('//') && assetBaseUrl) {
    return `${String(assetBaseUrl).replace(/\/+$/, '')}${url}`;
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
  return segments.length ? segments : [{ type: 'text', text: '' }];
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

    const fence = /^```\s*([\w-]*)\s*$/.exec(trimmed);
    if (fence) {
      flushParagraph();
      const codeLines = [];
      const language = fence[1] || '';
      lineIndex += 1;
      while (lineIndex < lines.length && !/^```\s*$/.test(lines[lineIndex].trim())) {
        codeLines.push(lines[lineIndex]);
        lineIndex += 1;
      }
      nodes.push({ id: nextId(), type: 'code', language, text: codeLines.join('\n') });
      continue;
    }

    const heading = /^(#{1,4})\s+(.+)$/.exec(trimmed);
    if (heading) {
      flushParagraph();
      const id = nextId();
      const node = { id, type: 'heading', level: heading[1].length, text: heading[2].trim() };
      nodes.push(node);
      headings.push({ id, level: node.level, text: node.text });
      continue;
    }

    const image = /^!\[([^\]]*)\]\((\S+?)(?:\s+["'][^"']*["'])?\)$/.exec(trimmed);
    if (image) {
      flushParagraph();
      const src = resolveAssetUrl(image[2], settings.assetBaseUrl);
      const node = { id: nextId(), type: 'image', alt: image[1] || '正文图片', src, originalSrc: src, failed: !src };
      nodes.push(node);
      if (src) images.push(src);
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
      const ordered = /^\d+[.)]\s+/.test(trimmed);
      const items = [];
      const itemPattern = ordered ? /^\d+[.)]\s+/ : /^[-*+]\s+/;
      while (lineIndex < lines.length && itemPattern.test(lines[lineIndex].trim())) {
        items.push({
          marker: ordered ? `${items.length + 1}.` : '•',
          segments: parseInline(lines[lineIndex].trim().replace(itemPattern, ''))
        });
        lineIndex += 1;
      }
      lineIndex -= 1;
      nodes.push({ id: nextId(), type: 'list', ordered, items });
      continue;
    }

    paragraph.push(line);
  }
  flushParagraph();
  return { nodes, headings, images };
}

module.exports = { parseMarkdown, parseInline, normalizeExternalUrl, resolveAssetUrl };
