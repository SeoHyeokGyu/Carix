import { useEffect, useRef } from "react";
import { Form, redirect, useActionData, useLoaderData, useNavigation } from "react-router";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Field } from "../../components/onboarding/Field";
import { NativeSelect } from "../../components/onboarding/NativeSelect";
import { StepBadges } from "../../components/onboarding/StepBadges";
import { toUserMessage } from "../../lib/apiError";
import { getProfile, patchProfile } from "../../lib/api/profile";
import { UnauthorizedError } from "../../lib/coreApi";
import { toFieldErrors, type FieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import { formDataToObject } from "../../lib/formData";
import { advanceOnboarding } from "../../lib/onboardingApi";
import { isForwardStep, LANDING_PATH, LOGIN_PATH } from "../../lib/routeGuards";
import {
  COMPANY_SIZES,
  EMPLOYMENT_TYPES,
  JOB_FAMILIES,
  PROFILE_FIELD_MESSAGES,
  profileSchema,
  REGIONS,
} from "../../lib/schemas/profile";
import { requireSession, setSession } from "../../lib/session";

type ActionData = { errors: FieldErrors };

export async function clientLoader() {
  try {
    return { profile: await getProfile() };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    // 조회 실패는 이 화면에 그릴 자리가 없다(아직 아무것도 입력하지 않았다) —
    // ErrorBoundary 몫이다.
    throw error;
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const intent = formData.get("intent");
  const raw = formDataToObject(formData, { numeric: ["yearsOfExperience"] });

  // "저장 후 나가기" 는 profileSchema.partial() 로(PATCH 는 부분 필드 저장을 허용한다 —
  // 전체 완비 검증은 검토 화면 몫이다), "다음" 은 profileSchema 전체로 검증한다.
  const schema = intent === "exit" ? profileSchema.partial() : profileSchema;
  const result = schema.safeParse(raw);
  if (!result.success) {
    return {
      errors: toFieldErrors(result.error.issues, PROFILE_FIELD_MESSAGES),
    } satisfies ActionData;
  }

  // 저장 구간만 감싼다 — safeParse 는 던지지 않으므로 바깥에 둔다. 401 은 로그인으로
  // 보내고(입력을 잃어도 어차피 세션이 없어 되돌아올 곳이 없다), 그 밖의 실패는 화면에
  // 남아 입력을 보존한 채 오류만 보여준다.
  try {
    await patchProfile(result.data);

    if (intent === "exit") {
      // 온보딩 단계·세션 캐시를 그대로 두고 랜딩으로 나간다 — 사용자는 여기서
      // 멈추는 것이고, 나중에 다시 로그인하면 guardOnboarding 이 같은 단계로
      // 돌려보내야 한다. 로그아웃도 하지 않는다. 목적지는 랜딩뿐이다 — 온보딩
      // 미완료 상태에서는 guardOnboarded 가 /dashboard 를 막고 현재 단계로
      // 돌려보내므로 그쪽으로 보내는 건 의미가 없다.
      return redirect(LANDING_PATH);
    }

    // guardOnboarding 은 세션 캐시(session.ts 의 모듈 전역)의 onboardingStep 을 읽는다.
    // 이미 이 단계를 지난 사용자가 검토 화면의 "수정" 링크로 돌아와 다시 "다음"을
    // 누르는 경우("이미 지난 단계를 고치는" 정당한 흐름)까지 여기서 무조건
    // advanceOnboarding 을 부르면, 서버 단계가 뒤로 밀렸다가 다음 화면 진입에서
    // 다시 앞으로 밀리기를 반복하며 결국 아직 손대지 않은 단계(예: 스킬)로 사용자를
    // 떠민다 — 그 단계는 화면 자체가 없어 오도 가도 못하고 갇힌다. 실제로 앞으로
    // 가는 경우에만 서버 단계·세션 캐시를 갱신한다.
    const session = await requireSession();
    if (session.status === "authenticated" && isForwardStep(session.onboardingStep, "INCOME")) {
      await advanceOnboarding("INCOME");
      setSession({ ...session, onboardingStep: "INCOME" });
    }

    return redirect("/onboarding/income");
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
  }
}

export default function Profile() {
  const { profile } = useLoaderData<typeof clientLoader>();
  const actionData = useActionData<ActionData>();
  const navigation = useNavigation();
  const errors = actionData?.errors;
  const isSubmitting = navigation.state === "submitting" || navigation.state === "loading";
  const formRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (errors) focusFirstInvalidField(formRef.current);
  }, [errors]);

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
          <Form method="post" ref={formRef} className="flex flex-col gap-6">
            <Field id="jobFamily" label="주 직무" error={errors?.jobFamily}>
              {(aria) => (
                <NativeSelect
                  {...aria}
                  name="jobFamily"
                  defaultValue={profile.jobFamily ?? ""}
                  placeholder="선택해 주세요"
                  options={JOB_FAMILIES}
                />
              )}
            </Field>

            <Field
              id="yearsOfExperience"
              label="총 경력 (년)"
              error={errors?.yearsOfExperience}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="yearsOfExperience"
                  type="number"
                  inputMode="numeric"
                  defaultValue={profile.yearsOfExperience ?? ""}
                />
              )}
            </Field>

            <Field id="region" label="희망 근무 지역" error={errors?.region}>
              {(aria) => (
                <NativeSelect
                  {...aria}
                  name="region"
                  defaultValue={profile.region ?? ""}
                  placeholder="선택해 주세요"
                  options={REGIONS}
                />
              )}
            </Field>

            <Field id="employmentType" label="고용 형태" error={errors?.employmentType}>
              {(aria) => (
                <NativeSelect
                  {...aria}
                  name="employmentType"
                  defaultValue={profile.employmentType ?? ""}
                  placeholder="선택해 주세요"
                  options={EMPLOYMENT_TYPES}
                />
              )}
            </Field>

            <Field id="companySize" label="회사 규모 (선택)" error={errors?.companySize}>
              {(aria) => (
                <NativeSelect
                  {...aria}
                  name="companySize"
                  defaultValue={profile.companySize ?? ""}
                  placeholder="선택 안 함"
                  options={COMPANY_SIZES}
                />
              )}
            </Field>

            {errors?._form && (
              <p role="alert" className="text-sm text-destructive">
                {errors._form}
              </p>
            )}

            {/* DOM 순서를 "다음" 이 먼저 오도록 둔다 — 한 필드에서 Enter 로 암묵
                제출하면 브라우저는 DOM 상 첫 번째 제출 버튼을 활성화한다. "저장 후
                나가기" 가 먼저면 Enter 만으로 부분 저장 후 랜딩으로 튕겨 나간다.
                시각 순서를 CSS 로 뒤집지 않는다 — flex-row-reverse 는 페인트
                순서만 바꾸고 DOM·포커스 순서는 그대로라, 키보드 사용자는 시각상
                오른쪽(다음)으로 먼저 Tab 이 가는 순서 불일치(WCAG 2.4.3)가
                생긴다. DOM 순서를 와이어프레임(01-profile.html, "다음" 이 먼저,
                왼쪽 정렬)과 그대로 맞춰 시각·DOM·포커스 순서를 전부 일치시킨다. */}
            <div className="flex flex-wrap gap-3">
              <Button type="submit" name="intent" value="next" disabled={isSubmitting}>
                다음
              </Button>
              <Button
                type="submit"
                name="intent"
                value="exit"
                variant="outline"
                disabled={isSubmitting}
              >
                저장 후 나가기
              </Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </section>
  );
}
