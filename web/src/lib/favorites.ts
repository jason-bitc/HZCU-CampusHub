import type { Favorite, Page } from "../types";
export function favoritesPage(raw: Page<Favorite>): Page<Favorite> {
  return {
    total: raw.total,
    page: raw.page,
    size: raw.size,
    items: raw.items.map((row) =>
      row.available
        ? { articleId: row.articleId, available: true, article: row.article }
        : { articleId: row.articleId, available: false },
    ),
  };
}
export function lastFavoritesPage(total: number, size: number) {
  return Math.max(1, Math.ceil(total / size));
}
