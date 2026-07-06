export type Widen<T> =
  T extends number ? number :
  T extends boolean ? boolean :
  T;

export type OptionalFields<T, K extends keyof T> =
  Omit<T, K> & Partial<Pick<T, K>>;

export type ToString<T> = 
 T extends number?'number':
 T extends boolean?'boolean':
 never;