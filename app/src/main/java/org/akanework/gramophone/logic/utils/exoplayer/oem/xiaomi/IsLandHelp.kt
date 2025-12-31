package org.akanework.gramophone.logic.utils.exoplayer.oem.xiaomi

import android.os.Bundle
import org.json.JSONObject

object IsLandHelp {
    /**
     * 小米超级岛之音乐岛分享配置
     * @param addpic 添加图标
     * @param pic 分享卡片图标
     * @param content 分享卡片内容
     * @param title 分享卡片标题
     * @param shareContent 分享到应用的内容
     * @param sharePic 分享到应用的图片 (目前不知道怎么分享图片，未测试)
     * @return 直接注入到媒体通知即可 */
    fun isLandMusicShare(
        addpic: Bundle? = null,
        pic: String = "miui_media_album_icon",
        content: String,
        title: String,
        shareContent: String,
        sharePic: String? = null,
    ): Bundle {
        val nfBundle = Bundle()
        val param = JSONObject()
        val paramV2 = JSONObject()
        val island = JSONObject()
        island.put("shareData", shareData(
            title = title,
            content = content,
            pic = pic,
            sharePic = sharePic,
            shareContent = shareContent
        )
        )

        paramV2.put("param_island",island)
        param.put("param_v2",paramV2)

        if (addpic != null ){ nfBundle.putBundle("miui.focus.pics", addpic) }
        nfBundle.putString("miui.focus.param.media",param.toString())
        return nfBundle
    }


    /**
     * 小米超级岛分享信息
     * @param content 内容
     * @param title 标题
     * @param pic 图片
     * @param shareContent 分享内容
     * @param sharePic 分享图片
     * */
    fun shareData(
        content: String,
        title: String,
        pic: String,
        shareContent: String,
        sharePic: String? = null,
    ): JSONObject{
        val json = JSONObject()
        json.put("content",content)
        json.put("title",title)
        json.put("pic",pic)
        json.put("shareContent",shareContent)
        sharePic?.let { json.put("sharePic", it) }
        return json
    }

}