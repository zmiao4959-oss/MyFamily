# Image 2 素材生成记录

以下素材均于 2026-07-16 使用 Codex 内置 OpenAI Image 2 能力原创生成。App 运行时不调用 OpenAI，不需要用户提供 OpenAI API Key。生成结果先保存为 PNG，再使用 Lanczos 缩放并以 WebP（质量 86–90）写入 Android 工程；图片不含文字，所有中文均由 Jetpack Compose 渲染。

统一风格：温暖米色、深胡桃木棕、柔和鼠尾草绿和少量陶土红；成熟、安静、可信的纸本档案插画，不模仿任何特定艺术家。

## `app_icon_art.webp`

- 用途：App 图标草案、Adaptive Icon 前景和各密度旧版图标
- 原始尺寸：1256 × 1256；工程尺寸：512 × 512，并输出 48/72/96/144/192 px 图标
- 完整提示词：

```text
Use case: logo-brand
Asset type: Android adaptive app icon artwork for “家忆”, a private family memory archive
Primary request: Create an original simple emblem combining an open memory album with a small three-branch family tree, readable at tiny app-icon size.
Style/medium: clean flat painted emblem with subtle paper texture, mature and trustworthy, not corporate, not childish
Composition/framing: perfectly centered square icon, bold simple silhouette, generous safe margin, no thin lines
Color palette: warm cream background, deep walnut brown, muted sage green, small dusty terracotta accent
Constraints: no text, no letters, no Chinese characters, no faces, no watermark, no mockup, no rounded-corner frame, no drop shadow outside the emblem
```

## `welcome_family_album.webp`

- 用途：连接页和首次空资料首页插画
- 原始尺寸：977 × 1641；工程最大尺寸：900 × 1500
- 完整提示词：

```text
Use case: illustration-story
Asset type: Android welcome and launch illustration for a private family memory archive app
Primary request: Create an original warm, quiet editorial illustration of an open family album resting on a wooden table, with a small branching family tree motif rising gently from the pages and a few abstract photo cards, suggesting memories across generations.
Scene/backdrop: soft warm cream background with subtle paper texture and generous calm negative space
Style/medium: refined hand-painted digital illustration, restrained contemporary Chinese editorial sensibility, mature and trustworthy, not childish
Composition/framing: portrait-friendly centered composition, main subject in lower two-thirds, ample breathing room around edges
Lighting/mood: soft morning light, peaceful, intimate, nostalgic without sadness
Color palette: warm beige, deep walnut brown, muted sage green, dusty terracotta
Constraints: no people with identifiable faces, no logos, no text, no Chinese characters, no watermark, no UI mockup, no photorealism, no excessive gradients, suitable for light and dark tinted surfaces
```

## `empty_people.webp`

- 用途：人物列表和空家族树
- 原始尺寸：1087 × 1450；工程最大尺寸：720 × 960
- 完整提示词：

```text
Use case: illustration-story
Asset type: Android empty-state illustration for an empty family member list
Primary request: Create an original quiet illustration of three blank oval portrait frames of different sizes connected by a gentle leafy branch, inviting the user to add the first family member.
Scene/backdrop: warm cream paper background
Style/medium: refined hand-painted editorial illustration, mature, restrained, warm family memory aesthetic
Composition/framing: centered compact composition with generous padding, simple enough for a 320dp empty state
Color palette: warm beige, walnut brown, muted sage, dusty terracotta
Constraints: portrait frames must be empty with no faces or silhouettes, no text, no Chinese characters, no watermark, not childish, no UI mockup
```

## `empty_records.webp`

- 用途：空记录、空时间线和搜索无结果
- 原始尺寸：1256 × 1256；工程最大尺寸：720 × 720
- 完整提示词：

```text
Use case: illustration-story
Asset type: Android empty-state illustration for an empty life-record list
Primary request: Create an original calm illustration of a closed linen journal, a fountain pen, one small blank photo card and a single pressed leaf, suggesting the first story is waiting to be recorded.
Scene/backdrop: warm cream paper background
Style/medium: refined hand-painted editorial illustration with subtle paper and linen texture, mature and trustworthy
Composition/framing: centered compact still life with generous padding, suitable for a 320dp empty state
Color palette: warm beige, walnut brown, muted sage green, dusty terracotta accent
Constraints: no text on the journal or photo, no Chinese characters, no watermark, no identifiable people, not childish, no UI mockup
```

## 默认人物头像

- 文件：`avatar_default_male.webp`、`avatar_default_female.webp`、`avatar_default_neutral.webp`
- 用途：人物没有自定义照片时显示；三张工程尺寸均为 512 × 512
- 三张提示词共享以下风格描述：

```text
Use case: illustration-story
Asset type: default Android family member avatar
Style/medium: original refined hand-painted editorial portrait, simplified natural features, mature and respectful, not photorealistic, not childish
Composition/framing: centered head-and-shoulders portrait in a square, front three-quarter view, generous margin, plain circular-feeling backdrop without an actual border
Lighting/mood: soft calm daylight, warm and trustworthy
Color palette: warm beige, walnut brown, muted sage green, dusty terracotta
Constraints: fictional non-identifiable person, no text, no Chinese characters, no watermark, no logo, no jewelry or culturally specific status symbol, no dramatic expression
```

各头像的 `Primary request`：

```text
A fictional adult Chinese man with short natural dark hair and a simple muted sage shirt, neutral friendly expression.
A fictional adult Chinese woman with shoulder-length natural dark hair and a simple dusty terracotta blouse, neutral friendly expression.
A fictional gender-neutral Chinese adult with softly cropped natural dark hair and a simple warm beige and sage top, neutral friendly expression; avoid strongly gendered styling.
```

## `family_tree_background.webp`

- 用途：可交互家族树边缘装饰
- 原始尺寸：1536 × 1024；工程尺寸：1280 × 854
- 完整提示词：

```text
Use case: illustration-story
Asset type: subtle Android family-tree screen background decoration
Primary request: Create an original sparse pattern of delicate branching twigs, small leaves and faint archival-paper fibers around the outer edges, leaving the entire center visually quiet for interactive family-tree nodes and connecting lines.
Scene/backdrop: seamless warm cream paper surface
Style/medium: restrained hand-painted editorial botanical decoration, mature and quiet
Composition/framing: wide landscape composition, decorations confined mostly to corners and edges, at least 70 percent clean central negative space
Color palette: very pale warm beige, muted sage, faint walnut brown at low contrast
Constraints: no people, no portraits, no frames, no tree diagram nodes, no text, no Chinese characters, no watermark, no strong contrast, must not compete with UI content
```

## `recording_interview_background.webp`

- 用途：口述采访和录音卡片背景
- 原始尺寸：941 × 1668；工程最大尺寸：720 × 1280
- 完整提示词：

```text
Use case: illustration-story
Asset type: Android recording interview mode background
Primary request: Create an original calm still-life illustration of a vintage but generic tabletop microphone beside an open blank interview notebook and one small leafy sprig, suggesting careful oral-history recording.
Scene/backdrop: warm cream paper background with a soft muted sage vignette
Style/medium: refined hand-painted editorial illustration, mature, trustworthy, restrained
Composition/framing: portrait-friendly, subject concentrated in lower half, large quiet space above for timer and controls
Lighting/mood: soft evening lamp light, attentive and intimate
Color palette: warm beige, walnut brown, muted sage, dusty terracotta
Constraints: no brand, no text on notebook, no Chinese characters, no waveform, no watermark, no identifiable person, not childish, no UI mockup
```

## `backup_complete.webp`

- 用途：空备份列表和备份完成提示
- 原始尺寸：1256 × 1256；工程最大尺寸：720 × 720
- 完整提示词：

```text
Use case: illustration-story
Asset type: Android data-backup-complete illustration
Primary request: Create an original reassuring illustration of a small archival storage box with a secure brass clasp, a neatly tied bundle of blank photo cards and a healthy green sprig, conveying that family memories are safely preserved.
Scene/backdrop: warm cream paper background
Style/medium: refined hand-painted editorial illustration with subtle paper, linen and wood textures, mature and trustworthy
Composition/framing: centered compact composition with generous padding, suitable for a success dialog or empty state
Lighting/mood: soft warm light, calm relief and safekeeping
Color palette: warm beige, walnut brown, muted sage green, restrained brass and dusty terracotta accents
Constraints: no lock icon, no cloud symbol, no brand, no text, no Chinese characters, no watermark, no faces, not childish, no UI mockup
```

## 后期处理说明

- 不裁切或重绘生成内容，只进行等比例缩放、RGB 转换和 WebP 压缩。
- App 图标额外生成 Android 旧版密度尺寸，并由 Adaptive Icon 引用同一原创图案。
- 素材中不嵌入任何中文，避免生成文字错误及无障碍问题。
- 原始 PNG 保留在 Codex 生成目录；仓库只提交优化后的 WebP。
