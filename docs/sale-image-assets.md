# 方案 C 主推活动图片

使用内置 imagegen，根据原始模特和商品图生成独立的横幅活动照片。只替换主推特惠图片区；标题、价格、折扣、按钮仍由网页渲染。全部特惠保留原商品图片，仅由 sale.js 分别延展左右（或上下）边缘像素来匹配容器背景，并随卡片尺寸重新绘制；不再对两侧颜色取平均，不修改原图或混合商品颜色。未新增后台或数据库结构。

资源目录：`src/main/resources/static/images/products/campaign-c/`。三张 JPEG 由生成原图编码，无文字且未裁切主体。只有原始示例图片路径会匹配对应活动图，管理员上传的新图仍走现有单图／双图展示。

## 生成提示词

Use case: ads-marketing. Generate a SINGLE standalone LANDSCAPE 16:9 promotional PHOTOGRAPH, NOT a webpage. NO TEXT WHATSOEVER: no headings, product names, prices, badges, buttons, logos, watermarks. Last reference is ONLY campaign-C art direction: warm sunlit limestone courtyard, gentle olive-leaf shadows, natural warm light from upper left, cohesive premium lifestyle campaign photograph. Other references identify exact products and people to preserve. Seamless composed photographic scene, not juxtaposed rectangular photos. No blank price pedestal and no empty text reserve. Fill the wide image with subjects and authentic warm architecture; subjects large with 5% safe margins, no clipping, rich accurate colours and tangible material. 

- **overshirt-hero**：A complete full-body male model wearing the reference olive overshirt and navy trousers stands on LEFT occupying 42% width, separate identical olive two-pocket overshirt on subtle wooden hanger on RIGHT occupying 42% width. Same garment construction, collar and buttons. Both model and garment are large and sharp; shoes and head and full garment fully inside frame.
- **tee-hero**：A complete full-body East Asian male model wearing the reference white crew neck tee and beige trousers stands on LEFT occupying 42% width, separate identical bright WHITE cotton tee on discreet hanger on RIGHT occupying 42% width. White shirt crisp against warm light limestone with soft natural shadow, never green or dark background. Keep complete head shoes sleeves and hem.
- **throw-hero**：One single folded cream woven cotton fringed throw exactly as first reference is the sole product, prominently arranged diagonally on a warm limestone bench, large plush woven detail and complete tassels, fills central 70% of wide composition. NO people, no duplicate product, no shirts, no mug.
