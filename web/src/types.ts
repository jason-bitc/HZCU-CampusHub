export interface Category {
  id: number;
  name: string;
}
export interface Catalog {
  jobs: Category[];
  majors: Category[];
  companies: Category[];
  topics: Category[];
}
export interface Link {
  label: string;
  url: string;
}
export interface Article {
  id: number;
  title: string;
  summary: string;
  categories: Catalog;
  experienceStage: string;
  sourceType: string;
  publishedAt: string;
  updatedAt: string;
  counts: { effectiveCoins: number; effectiveLikes: number; origin: string };
}
export interface Detail extends Article {
  body: string;
  bodyFormat: string;
  sourceNote: string;
  sourceLinks: Link[];
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface Me {
  userId: number;
  email: string;
  role: string;
  accountState: string;
  campusCertification: string;
}
export interface Login {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
}
export interface Profile {
  revision: number;
  nickname: string;
  majorId: number | null;
  currentStage: string;
  bio: string;
}
export interface Contact {
  revision: number;
  enabled: boolean;
  type: "WECHAT" | "QQ" | "WEB" | null;
  value: string | null;
}
export type ArticleState =
  "DRAFT" | "PUBLISHED" | "AUTHOR_OFFLINE" | "ADMIN_HIDDEN" | "DELETED";
export interface Owned {
  articleId: number;
  state: ArticleState;
  revision: number;
  title: string;
  updatedAt: string;
}
export interface Form {
  title: string;
  summary: string;
  body: string;
  jobIds: number[];
  majorIds: number[];
  companyIds: number[];
  topicIds: number[];
  experienceStage: string;
  sourceType: string | null;
  sourceNote: string;
  sourceLinks: Link[];
}
export interface ImageAsset {
  imageId: string;
  url: string;
  mimeType: string;
  sizeBytes: number;
  width: number;
  height: number;
}
export interface Draft {
  articleId: number;
  state: ArticleState;
  revision: number;
  content: Omit<Form, "jobIds" | "majorIds" | "companyIds" | "topicIds"> & {
    tags: Pick<Form, "jobIds" | "majorIds" | "companyIds" | "topicIds">;
  };
  images: ImageAsset[];
  referencedImageIds: string[];
}
export type Favorite =
  | { articleId: number; available: true; article: Article }
  | { articleId: number; available: false };
export interface Challenge {
  challengeId: string;
  expiresAt: string;
  resendAt: string;
  notice: string;
}
