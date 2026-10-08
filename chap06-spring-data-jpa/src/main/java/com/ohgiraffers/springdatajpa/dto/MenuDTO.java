package com.ohgiraffers.springdatajpa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/* 설명. 엔티티를 그대로 응답하지 않고 DTO로 변환해서 주고받는다.
 *  ----------------------------------------------------------------------------------
 *  1) 엔티티를 그대로 반환하면 연관 관계를 타고 들어가며 불필요한 데이터까지 직렬화되고,
 *     양방향 연관 관계인 경우 무한 재귀에 빠지기도 한다.
 *  2) 엔티티의 필드 구조가 바뀌면 API 응답 스펙까지 함께 바뀌어버린다.
 *  ----------------------------------------------------------------------------------
 *  이 DTO는 Menu 엔티티와 달리 categoryCode, categoryName을 평평하게(flat) 가지고 있다.
 *  화면에서 쓰기 편한 형태와 DB 테이블 구조가 다를 수 있다는 점을 보여주는 부분이다.
 * */
public class MenuDTO {

	private int menuCode;
	private String menuName;
	private int menuPrice;
	private int categoryCode;
	private String categoryName;
	private String orderableStatus;
	@Schema(description = "메뉴 사진 조회 주소. 사진이 없으면 null입니다.", accessMode = Schema.AccessMode.READ_ONLY)
	private String imageUrl;

    private String menuIngredients;
    private String menuDescription;

	public MenuDTO() {}

	public MenuDTO(int menuCode, String menuName, int menuPrice,
				   int categoryCode, String categoryName, String orderableStatus) {
		this.menuCode = menuCode;
		this.menuName = menuName;
		this.menuPrice = menuPrice;
		this.categoryCode = categoryCode;
		this.categoryName = categoryName;
		this.orderableStatus = orderableStatus;
	}

	public int getMenuCode() {
		return menuCode;
	}

	public void setMenuCode(int menuCode) {
		this.menuCode = menuCode;
	}

	public String getMenuName() {
		return menuName;
	}

	public void setMenuName(String menuName) {
		this.menuName = menuName;
	}

	public int getMenuPrice() {
		return menuPrice;
	}

	public void setMenuPrice(int menuPrice) {
		this.menuPrice = menuPrice;
	}

	public int getCategoryCode() {
		return categoryCode;
	}

	public void setCategoryCode(int categoryCode) {
		this.categoryCode = categoryCode;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}

	public String getOrderableStatus() {
		return orderableStatus;
	}

	public void setOrderableStatus(String orderableStatus) {
		this.orderableStatus = orderableStatus;
	}

	public String getImageUrl() { return imageUrl; }

	public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

	@Override
	public String toString() {
		return "MenuDTO{" +
				"menuCode=" + menuCode +
				", menuName='" + menuName + '\'' +
				", menuPrice=" + menuPrice +
				", categoryCode=" + categoryCode +
				", categoryName='" + categoryName + '\'' +
				", orderableStatus='" + orderableStatus + '\'' +
				'}';
	}
    public String getMenuIngredients() { return menuIngredients; }
    public void setMenuIngredients(String value) { menuIngredients = value; }
    public String getMenuDescription() { return menuDescription; }
    public void setMenuDescription(String value) { menuDescription = value; }

}
