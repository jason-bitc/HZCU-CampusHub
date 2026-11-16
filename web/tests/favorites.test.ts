import assert from "node:assert/strict";
import test from "node:test";
import { favoritesPage, lastFavoritesPage } from "../src/lib/favorites.ts";
import type { Favorite, Page } from "../src/types.ts";
test("unavailable favorites strictly discard historical and unexpected content fields", () => {
  const raw = {
    items: [
      {
        articleId: 12,
        available: false,
        article: { title: "must not survive" },
        body: "private past content",
        updatedAt: "old",
      },
    ],
    total: 1,
    page: 1,
    size: 10,
  } as unknown as Page<Favorite>;
  assert.deepEqual(favoritesPage(raw).items, [
    { articleId: 12, available: false },
  ]);
});
test("removing last item can return from an empty last page, including zero collections", () => {
  assert.equal(lastFavoritesPage(6, 5), 2);
  assert.equal(lastFavoritesPage(5, 5), 1);
  assert.equal(lastFavoritesPage(0, 5), 1);
});
