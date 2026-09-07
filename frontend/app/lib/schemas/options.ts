export interface Option {
  value: string;
  label: string;
}

export const values = (options: Option[]) =>
  options.map((o) => o.value) as [string, ...string[]];
