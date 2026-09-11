import { coreGet, coreSend } from "../coreApi";
import type { ProfileInput } from "../schemas/profile";

export interface ProfileResponse {
  jobFamily: string | null;
  yearsOfExperience: number | null;
  region: string | null;
  employmentType: string | null;
  companySize: string | null;
}

export function getProfile() {
  return coreGet<ProfileResponse>("/v1/profile");
}

export function patchProfile(input: Partial<ProfileInput>) {
  return coreSend<ProfileResponse>("PATCH", "/v1/profile", input);
}
