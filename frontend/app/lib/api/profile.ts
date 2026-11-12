import { coreGet, coreSend } from "../coreApi";
import type { ProfileInput } from "../schemas/profile";
import type { RetirementPensionType } from "./scenario";

export interface ProfileResponse {
  jobFamily: string | null;
  yearsOfExperience: number | null;
  region: string | null;
  employmentType: string | null;
  companySize: string | null;
  /** 현 직장 입사일 `yyyy-MM-dd`. */
  hireDate: string | null;
}

export function getProfile() {
  return coreGet<ProfileResponse>("/v1/profile");
}

export function patchProfile(
  input: Partial<ProfileInput> & {
    hireDate?: string;
    retirementPensionType?: RetirementPensionType;
  },
) {
  return coreSend<ProfileResponse>("PATCH", "/v1/profile", input);
}
