import { Link } from 'react-router'

export default function TermsPage() {
  return (
    <div className="narrow">
      <div className="page-head">
        <h1 className="title2 title2--bold">메뉴판 실습 이용약관</h1>
        <p className="body2">카카오싱크 동의 흐름을 연습하기 위한 학습용 약관이에요.</p>
      </div>
      <article className="form-card auth-account body2">
        <h2 className="headline1 headline1--bold">서비스 이용</h2>
        <p>이 메뉴판은 메뉴 관리와 소셜 로그인 연동을 배우기 위한 개인 실습 서비스입니다. 등록한 메뉴와 회원 정보는 실습 목적으로만 사용합니다.</p>
        <h2 className="headline1 headline1--bold">회원 정보</h2>
        <p>카카오 로그인으로 확인한 회원번호, 동의한 닉네임, 가입·로그인 시각을 보관합니다. 카카오싱크 연습에서는 약관 태그와 동의 여부·시각도 보관합니다.</p>
        <h2 className="headline1 headline1--bold">로그인과 결제</h2>
        <p>로그아웃하면 이 사이트의 로그인 상태가 종료됩니다. 카카오 계정 자체의 로그인 상태는 유지될 수 있습니다. 카카오페이 결제는 테스트 결제입니다.</p>
        <h2 className="headline1 headline1--bold">학습용 안내</h2>
        <p>이 문서는 실제 사업 서비스 운영을 위한 약관이 아닙니다. 실제 서비스를 운영할 때는 제공 기능과 개인정보 처리 방식에 맞는 별도 약관을 준비해야 합니다.</p>
        <Link className="btn btn-outline label1" to="/menus">메뉴 목록으로</Link>
      </article>
    </div>
  )
}
