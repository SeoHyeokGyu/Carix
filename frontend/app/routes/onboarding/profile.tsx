import { Form, redirect, useActionData, useLoaderData, useNavigation } from "react-router";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { StepBadges } from "../../components/onboarding/StepBadges";
import { getProfile, patchProfile } from "../../lib/api/profile";
import { advanceOnboarding } from "../../lib/onboardingApi";
import {
  COMPANY_SIZES,
  EMPLOYMENT_TYPES,
  JOB_FAMILIES,
  profileSchema,
  REGIONS,
  type ProfileInput,
} from "../../lib/schemas/profile";
import { requireSession, setSession } from "../../lib/session";

type FieldErrors = Partial<Record<keyof ProfileInput, string>>;
type ActionData = { errors: FieldErrors } | { saved: true };

const FIELD_ERROR_MESSAGES: Record<keyof ProfileInput, string> = {
  jobFamily: "주 직무를 선택해 주세요.",
  yearsOfExperience: "총 경력은 0~50 사이의 정수로 입력해 주세요.",
  region: "희망 근무 지역을 선택해 주세요.",
  employmentType: "고용 형태를 선택해 주세요.",
  companySize: "회사 규모를 올바르게 선택해 주세요.",
};

const SELECT_CLASS =
  "h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30";

export async function clientLoader() {
  const profile = await getProfile();
  return { profile };
}

/**
 * FormData → 후보 객체. 빈 문자열(미입력·미선택)은 아예 키를 만들지 않는다 — "다음"
 * 은 profileSchema(필수) 로, "저장 후 나가기" 는 profileSchema.partial() 로 검증하는데,
 * 빈 문자열을 그대로 넣으면 partial 에서도 "필드가 있는데 값이 유효하지 않다" 로 걸린다.
 */
function readProfileForm(formData: FormData): Record<string, unknown> {
  const raw: Record<string, unknown> = {};

  const jobFamily = formData.get("jobFamily");
  if (typeof jobFamily === "string" && jobFamily !== "") raw.jobFamily = jobFamily;

  const yearsOfExperience = formData.get("yearsOfExperience");
  if (typeof yearsOfExperience === "string" && yearsOfExperience !== "") {
    const parsed = Number(yearsOfExperience);
    if (Number.isFinite(parsed)) raw.yearsOfExperience = parsed;
  }

  const region = formData.get("region");
  if (typeof region === "string" && region !== "") raw.region = region;

  const employmentType = formData.get("employmentType");
  if (typeof employmentType === "string" && employmentType !== "")
    raw.employmentType = employmentType;

  const companySize = formData.get("companySize");
  if (typeof companySize === "string" && companySize !== "")
    raw.companySize = companySize;

  return raw;
}

function toFieldErrors(issues: { path: PropertyKey[] }[]): FieldErrors {
  const errors: FieldErrors = {};
  for (const issue of issues) {
    const field = issue.path[0];
    if (typeof field !== "string" || field in errors) continue;
    if (field in FIELD_ERROR_MESSAGES) {
      errors[field as keyof ProfileInput] =
        FIELD_ERROR_MESSAGES[field as keyof ProfileInput];
    }
  }
  return errors;
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const intent = formData.get("intent");
  const raw = readProfileForm(formData);

  // "저장 후 나가기" — PATCH 는 부분 필드 저장을 허용한다(전체 완비 검증은 검토 화면
  // 몫이다). 온보딩 단계는 넘기지 않는다 — 이 버튼은 이탈 시 유실 방지용이지 "다음"
  // 이 아니다.
  // NOTE: (cjkang) 어디로 이동할지는 명세·와이어프레임 어디에도 없다 — 그래서 이동하지
  // 않는다. 저장 성공 뱃지만 보여주고 실제로 나가는 것(닫기·다른 경로 이동)은 사용자
  // 손에 맡긴다. 목적지가 정해지면 여기서 redirect 를 추가한다.
  if (intent === "exit") {
    const result = profileSchema.partial().safeParse(raw);
    if (!result.success) {
      return { errors: toFieldErrors(result.error.issues) } satisfies ActionData;
    }
    await patchProfile(result.data);
    return { saved: true } satisfies ActionData;
  }

  const result = profileSchema.safeParse(raw);
  if (!result.success) {
    return { errors: toFieldErrors(result.error.issues) } satisfies ActionData;
  }

  await patchProfile(result.data);
  await advanceOnboarding("INCOME");

  // guardOnboarding 은 세션 캐시(session.ts 의 모듈 전역)의 onboardingStep 을 읽는다.
  // 여기서 캐시를 갱신하지 않으면 온보딩 레이아웃 가드가 여전히 PROFILE 로 보고 다음
  // 화면 진입을 이 화면으로 되튕긴다.
  const session = await requireSession();
  if (session.status === "authenticated") {
    setSession({ ...session, onboardingStep: "INCOME" });
  }

  return redirect("/onboarding/income");
}

export default function Profile() {
  const { profile } = useLoaderData<typeof clientLoader>();
  const actionData = useActionData<ActionData>();
  const navigation = useNavigation();
  const errors = actionData && "errors" in actionData ? actionData.errors : undefined;
  const saved = actionData && "saved" in actionData && actionData.saved;
  const isSubmitting = navigation.state === "submitting";

  return (
    <section className="mx-auto w-full max-w-3xl px-6 py-16">
      <StepBadges current={1} />

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>프로필</CardTitle>
          <CardDescription>
            동료집단 비교를 위해 직군·총 경력·지역·고용 형태를 입력해 주세요.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form method="post" className="flex flex-col gap-6">
            <div className="flex flex-col gap-2">
              <Label htmlFor="jobFamily">주 직무</Label>
              <select
                id="jobFamily"
                name="jobFamily"
                defaultValue={profile.jobFamily ?? ""}
                aria-invalid={Boolean(errors?.jobFamily)}
                className={SELECT_CLASS}
              >
                <option value="">선택해 주세요</option>
                {JOB_FAMILIES.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {errors?.jobFamily && (
                <p className="text-sm text-destructive">{errors.jobFamily}</p>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="yearsOfExperience">총 경력 (년)</Label>
              <Input
                id="yearsOfExperience"
                name="yearsOfExperience"
                type="number"
                inputMode="numeric"
                defaultValue={profile.yearsOfExperience ?? ""}
                aria-invalid={Boolean(errors?.yearsOfExperience)}
              />
              {errors?.yearsOfExperience && (
                <p className="text-sm text-destructive">{errors.yearsOfExperience}</p>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="region">희망 근무 지역</Label>
              <select
                id="region"
                name="region"
                defaultValue={profile.region ?? ""}
                aria-invalid={Boolean(errors?.region)}
                className={SELECT_CLASS}
              >
                <option value="">선택해 주세요</option>
                {REGIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {errors?.region && (
                <p className="text-sm text-destructive">{errors.region}</p>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="employmentType">고용 형태</Label>
              <select
                id="employmentType"
                name="employmentType"
                defaultValue={profile.employmentType ?? ""}
                aria-invalid={Boolean(errors?.employmentType)}
                className={SELECT_CLASS}
              >
                <option value="">선택해 주세요</option>
                {EMPLOYMENT_TYPES.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {errors?.employmentType && (
                <p className="text-sm text-destructive">{errors.employmentType}</p>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="companySize">회사 규모 (선택)</Label>
              <select
                id="companySize"
                name="companySize"
                defaultValue={profile.companySize ?? ""}
                aria-invalid={Boolean(errors?.companySize)}
                className={SELECT_CLASS}
              >
                <option value="">선택 안 함</option>
                {COMPANY_SIZES.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {errors?.companySize && (
                <p className="text-sm text-destructive">{errors.companySize}</p>
              )}
            </div>

            {saved && (
              <Alert>
                <AlertDescription>
                  <Badge variant="secondary">저장됨</Badge>
                  <span>입력한 내용이 저장되었습니다. 이어서 진행하거나 나가도 됩니다.</span>
                </AlertDescription>
              </Alert>
            )}

            <div className="flex flex-wrap justify-end gap-3">
              <Button
                type="submit"
                name="intent"
                value="exit"
                variant="outline"
                disabled={isSubmitting}
              >
                저장 후 나가기
              </Button>
              <Button type="submit" name="intent" value="next" disabled={isSubmitting}>
                다음
              </Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </section>
  );
}
