# 通用商品照片展示组件

引入 `/css/product-showcase.css`，再使用 `fragments/product-showcase :: showcase(url, name, modelImage, productImage, index, natural)`。

- `url`：点击后的商品地址；`name`：可访问的商品名称。
- `modelImage` / `productImage`：模特照、商品照。两张不同照片并排；缺一张或地址相同则居中单图；都没有则显示名称。
- `index`：从 0 开始，首项立即加载，其余延迟加载。
- `natural`：浅色棚拍使用 `false`，统一到米灰色底；深色、实景、需要严格保留浅色商品色彩的照片使用 `true`，关闭混合效果。此组件不自动抠图，不能保证任意照片都无缝融合。
- 可通过 `--photo-stage` 调整底色，默认 `#e7e2d8`。

桌面展示区最小高度 340px，模特／商品列宽约 47:53，商品图片高度为模特的 87%。所有照片使用 contain，保留完整主体。600px 以下缩小高度与内边距，双图保持并排；图文上下排列由外层页面布局控制。

当前仅接入特惠页主推区域，不改变商品详情图及真实图片文件。
