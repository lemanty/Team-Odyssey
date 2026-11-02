# 首页帆船与海浪循环动画

首页自动播放 24 秒循环。帆船在水面上左右缓行、起伏和轻晃，倒影跟随；
海面在限定区域内折射运动，建筑、陶器和岸线保持静止。
已移除播放/暂停按钮。main 不引用光标动画，也不包含光标动画源文件。

采用 Canvas 分层渲染，不是生成的视频。原图保留为无脚本、减少动态效果、
加载失败时的静态回退。离屏、后台、打印和页面离开时停止绘制；恢复后继续。
所有运动周期都是 24 秒的约数，循环首尾一致。Canvas 分辨率最高为 CSS 尺寸的 1.5 倍。

## 资源

- 原始图：src/main/resources/static/images/odyssey-voyage.png
- 生成底图：src/main/resources/static/images/odyssey-voyage-sea.png
- 实现：static/js/hero-motion.js、static/css/hero-motion.css、templates/home.html
- 图片生成方式：内置 image_gen，precise-object-edit。最终提示词：
  “Edit target: attached square photograph of an ivory arch overlooking the Aegean Sea.
  Create a clean background plate for animation. Remove ONLY the small sailboat
  centered near x=745 y=568 of the 1254x1254 image, its wake and its bright vertical
  reflection below it. Fill that small region with continuous natural sea matching
  the surrounding water. Preserve EXACT original composition, arch, horizon, coast,
  olive branches, pottery, linen, light, colours, texture, framing. Do not add any boat
  or object. Same square aspect ratio. Photorealistic, seamless removal, no text.”

## 验证

node --test scripts/tests/hero-motion.test.cjs
./mvnw -Dtest=HomeHeroMotionTests test

浏览器检查自动播放、独立船体与海面、静止前景、控制按钮和光标动画不加载。
