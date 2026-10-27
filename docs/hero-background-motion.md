# 首页背景：克制的循环微动

基于提交 `11285ae9bbd1bb2b626bd9520afa058c2dfd5d85`，独立预览分支为 `feature/hero-ambient-motion-20261027`。本分支不修改 main；未合并、部署或创建 PR。

## 效果

- 原始 `odyssey-voyage.png` 和 `voyage-reveal.svg` 字节完全不变。
- 原羽化蒙版从图片移到同尺寸的 `.hero-art` 外层；静态时图片位置、尺寸、裁切不变，动画时渐变边界固定。
- 桌面 46 秒一圈：均匀缩放最多 1.008 倍，平移最多宽度 0.18% / 高度 0.10%；光层透明度只在 0.02–0.045 之间变化。
- 手机（≤600px）56 秒一圈，最大缩放 1.004 倍，平移 0.06% / 0.04%，光层减半。
- 起点和终点一致，ease-in-out 平缓往返。不是海浪视频，也没有局部拉伸、建筑变形、船的独立移动。
- 标题、文字、按钮不参与动画；共享入场脚本也排除整个首屏。

## 可访问性与性能

语义化暂停/播放按钮有中英文文字、键盘焦点样式、44px 高度；暂停会保持当前帧。监测图片本身是否在视窗中，而不是只检测手机屏幕上方的文案。切到后台、离开画面、打印、pagehide 时暂停；恢复后仍尊重手动暂停。支持 back/forward cache。

系统开启“减少动态效果”时立即恢复静态，隐藏动画按钮。JS 禁用、缺少 IntersectionObserver 或不支持实时偏好监听时保留原始静态画面，不显示无效控件。没有第三方依赖、计时器、滚动处理器、localStorage、音频或视频。刷新页面会重置手动暂停选择。

## 接入

先确保当前工作区没有未保存的修改，然后检出独立分支（无需再次应用补丁）：

```bash
git fetch origin feature/hero-ambient-motion-20261027
git switch --track origin/feature/hero-ambient-motion-20261027
node --test scripts/tests/hero-motion.test.cjs
./mvnw verify
./mvnw spring-boot:run
```

修改涵盖 home.html、style.css、共享入场脚本和其缓存版本；新增 hero-motion.css / hero-motion.js。两份测试随分支添加。没有修改原图、蒙版、数据库或业务代码。

## 验证记录 / 2026-10-27

**已通过**：18 项 Node 内置测试（DOM mock，非浏览器测试）；JS 语法检查；git diff --check；补丁对原始基线的 apply --check；原图与蒙版 SHA-256 一致。预览的外层与 iframe HTML 解析、嵌入资源、脚本语法、静态对比开关及重复切换通过程序检查。

**未完成**：真实浏览器渲染/视觉验收。CUA 打开新的本地 HTTP 服务返回 `net::ERR_CONNECTION_REFUSED`，另一次 shell 调用也无法连接该服务；未使用被禁止的 file:// 路线。没有声称看过动画在浏览器里的实际播放效果。

**构建阻塞**：Maven 默认缓存目录只读，使用可写 MAVEN_USER_HOME / maven.repo.local 后已越过该问题，但无法解析 `repo.maven.apache.org`（Unknown host），因此 `./mvnw verify` 未执行编译与 JUnit。随分支添加的 4 个 MockMvc 检查尚未运行。

## 打开预览

离线预览包单独交付，不包含在本分支；若已有该包，解压后双击 `odyssey-hero-preview.html`，用现代浏览器打开。它自含原图、原蒙版、CSS、JS 和缩小后的中文字体子集；不依赖项目服务。顶部可切换真实 390px iframe 的手机预览、桌面预览和静态原图对比。页内按钮可暂停/播放；向下滚动可验证离屏暂停。导航和商品按钮是预览占位，不连接线上业务。

预览只展示首页首屏，不是完整运行中的网站。为离线显示而嵌入的中文字体子集更名为 Odyssey Preview Subset，并保留原 SIL OFL 许可证；实际项目字体不变。

浏览器可用后建议核对：桌面/390px 手机、中文/英文、键盘操作、静态对比、减弱动态偏好、离屏与后台、往返导航、整圈首尾接缝。确保手机右下方暂停按钮没有盖住重要内容。
