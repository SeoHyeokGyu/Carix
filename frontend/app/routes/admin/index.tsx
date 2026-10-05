import { redirect } from "react-router";

export function clientLoader() {
  return redirect("/admin/data-health");
}

export default function AdminIndex() {
  return null;
}
