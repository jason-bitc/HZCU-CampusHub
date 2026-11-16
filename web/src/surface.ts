// Each build and port mounts one audience only; backend authorization remains authoritative.
declare const __CAMPUSHUB_SURFACE__: "user" | "admin" | "ops";
export const surface = __CAMPUSHUB_SURFACE__;
