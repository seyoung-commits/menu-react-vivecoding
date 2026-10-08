package com.ohgiraffers.springdatajpa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/* 설명. CORS (Cross-Origin Resource Sharing)와 SOP (Same-Origin Policy)
 *  1. Origin(오리진)이란?
 *    - URL의 구성 요소 중, 프로토콜(https), 호스트(localhost), 포트(8080)를 묶어 '오리진(origin; 요청의 출처)'이라고 부른다.
 *    - 예: http://localhost:5173 (프로토콜: http, 호스트: localhost, 포트: 5173)
 *  2. SOP (Same-Origin Policy, 동일 출처 정책)
 *    - 브라우저(Chrome, Edge 등)는 보안상의 이유로 '동일한 오리진'끼리만 자원을 공유할 수 있도록 제한하는 정책을 가진다.
 *    - 웹 페이지(HTML/JS)의 오리진과 해당 페이지가 API를 요청하는 서버의 오리진이 다를 경우 기본적으로 요청이 차단된다.
 *      (그렇지 않으면 각종 해킹 공격이 가능해진다. 이는 추후 교과목에서 다룬다)
 *  3. 프로젝트의 구성 현황
 *    - 프론트엔드(React): http://localhost:5173
 *    - 백엔드(Spring Boot): http://localhost:8080
 *    - 우리는 브라우저에 localhost:5173을 입력해 React 앱에 접속하고,
 *      React는 Spring Boot인 localhost:8080에 API를 요청해 데이터를 가져와 마저 화면을 구성하게 된다
 *    - 이때, 두 서버의 '포트 번호'가 다르기 때문에 브라우저 입장에서 이를 'Cross-Origin' 상태로 판단하고 차단한다.
 *  4. CORS 설정의 역할
 *    - 아래의 설정은 백엔드 서버에서 "http://localhost:5173 오리진은 안전하니까 통신을 허용해줘"라고 브라우저에게 알려주는 역할을 한다.
 * */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /*
     * 설명. WebMvcConfigurer 인터페이스
     * Spring MVC의 기본 설정을 유지하면서 특정 부분만 커스터마이징할 때 사용하는 인터페이스이다.
     * 인터셉터 추가, 리소스 핸들러 설정, CORS 설정 등 다양한 MVC 관련 설정을 메서드 오버라이딩을 통해 구현할 수 있다.
     */

    /*
     * 설명. addCorsMappings(CorsRegistry registry): CORS 설정을 정의하기 위해 사용하는 메서드이다.
     * 파라미터인 CorsRegistry는 단어 뜻 그대로 CORS 설정을 등록하는 객체로,
     * 특정 URL 경로에 대해 허용할 오리진, HTTP 메서드, 헤더 등을 설정할 수 있게 해준다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {

        // 모든 경로(/**)에 대해 CORS 설정을 적용한다.
        registry.addMapping("/**")
                // 요청을 허용할 오리진(도메인+포트)을 지정한다. (Vite 기본 포트인 5173 허가)
                .allowedOrigins("http://localhost:5173")
                // 허용할 HTTP 메서드를 지정한다. (RESTful API의 주요 메서드들)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 모든 헤더 정보를 허용한다.
                .allowedHeaders("*")
                // 자격 증명(Cookie 등)을 포함한 요청을 허용한다.
                .allowCredentials(true);
    }
}
