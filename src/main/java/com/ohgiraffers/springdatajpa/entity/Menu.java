package com.ohgiraffers.springdatajpa.entity;

import jakarta.persistence.*;

@Entity
@Table(name="tbl_menu")
public class Menu {

	@Id
	@Column(name="menu_code")
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private int menuCode;

	@Column(name="menu_name")
	private String menuName;

	@Column(name="menu_price")
	private int menuPrice;

	@Column(name="orderable_status")
	private String orderableStatus;

	@Column(name = "image_path", length = 255)
	private String imagePath;

	/* 설명. 카테고리와의 연관 관계 (N:1)
	 *  ----------------------------------------------------------------------------------
	 *  하나의 DB 컬럼(category_code)은 하나의 필드에만 매핑한다.
	 *  즉 'private int categoryCode' 같은 필드를 따로 두지 않고,ㅔ 이 연관 관계 필드 하나로만 다룬다.
	 *  ----------------------------------------------------------------------------------
	 *  주의. 같은 컬럼에 스칼라 필드와 연관 관계 필드를 둘 다 두면
	 *       한쪽에 insertable=false, updatable=false를 붙여 읽기 전용으로 만들어야 하는데,
	 *       이때 읽기 전용 필드의 값을 바꾸면 아무 오류 없이 조용히 무시된다.
	 *       (수정은 성공했다고 응답하는데 DB는 그대로인, 찾기 어려운 버그로 이어진다)
	 *       따라서 외래키는 연관 관계 필드 하나로만 다루는 편이 안전하다.
	 * */
	@ManyToOne
	@JoinColumn(name = "category_code")
	private Category category;

    @Column(name = "menu_ingredients", length = 1000)
    private String menuIngredients;
    @Column(name = "menu_description", length = 2000)
    private String menuDescription;

	public Menu() {}

	public Menu(int menuCode, String menuName, int menuPrice, String orderableStatus, Category category) {
		this.menuCode = menuCode;
		this.menuName = menuName;
		this.menuPrice = menuPrice;
		this.orderableStatus = orderableStatus;
		this.category = category;
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

	public String getOrderableStatus() {
		return orderableStatus;
	}

	public void setOrderableStatus(String orderableStatus) {
		this.orderableStatus = orderableStatus;
	}

	public String getImagePath() { return imagePath; }

	public void setImagePath(String imagePath) { this.imagePath = imagePath; }

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	@Override
	public String toString() {
		return "Menu{" +
				"menuCode=" + menuCode +
				", menuName='" + menuName + '\'' +
				", menuPrice=" + menuPrice +
				", orderableStatus='" + orderableStatus + '\'' +
				// 연관 객체는 코드만 출력하여 양방향 관계의 무한 참조를 방지한다.
				", category=" + (category != null ? category.getCategoryCode() : null) +
				'}';
	}
    public String getMenuIngredients() { return menuIngredients; }
    public void setMenuIngredients(String value) { menuIngredients = value; }
    public String getMenuDescription() { return menuDescription; }
    public void setMenuDescription(String value) { menuDescription = value; }

}
