/**
 * 실패한 제출 뒤 첫 번째 잘못된 필드로 포커스를 옮긴다. `<Field>` 가 오류가 있는
 * 컨트롤에 `aria-invalid="true"` 를 붙여 주므로 그 컨트롤을 찾아 포커스만
 * 옮기면 된다. 필드가 많은 화면(소비 입력 등)에서는 오류 문단이 화면 밖에
 * 있을 수 있어, 스크린 리더 사용자뿐 아니라 일반 사용자도 이게 없으면 오류를
 * 놓칠 수 있다.
 */
export function focusFirstInvalidField(form: HTMLFormElement | null): void {
  form?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
}
