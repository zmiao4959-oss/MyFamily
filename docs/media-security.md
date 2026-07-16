# 媒体存储与安全

## 存储目录

- Android 原始文件：App 私有目录 `files/media/original` 与 `files/media/recordings`。
- Android 本地预览：App 私有目录 `files/media/thumbnails`。
- 服务端原件：`MEDIA_ROOT/original`。
- 服务端缩略图和视频封面：`MEDIA_ROOT/thumbnails`。
- 未完成分块：`MEDIA_ROOT/.uploads`。

数据库只保存相对服务端路径，不向客户端返回真实磁盘路径。服务端文件名由 UUID 生成；用户原文件名仅作为经过校验的显示元数据。

## 上传校验

1. 初始化时验证关联记录、允许的 MIME 和最大文件大小。
2. 拒绝包含路径分隔符、控制字符或路径穿越的原文件名。
3. 每个分块必须从服务器返回的准确偏移继续，单块最大 1 MB。
4. 完成时再次核对实际字节数和文件签名，防止仅伪造 `Content-Type`。
5. 计算 SHA-256；客户端提供哈希时必须一致。
6. 相同 SHA-256 的内容复用物理原件，但为不同记录保留独立附件元数据。
7. 原件与缩略图下载均要求有效 Bearer Token。

## 支持类型

- 图片：JPEG、PNG、WebP、HEIC/HEIF（设备或服务端无法解码时仍保留原图，但可能暂时没有缩略图）。
- 音频：M4A/MP4、AAC、MP3、WAV、Ogg。
- 视频：MP4、WebM、QuickTime MOV。

无法生成缩略图不会删除原始媒体；音频和视频元数据探测失败也不阻止原件入库。
