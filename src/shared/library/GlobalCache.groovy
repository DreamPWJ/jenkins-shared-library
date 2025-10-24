#!/usr/bin/env groovy
package shared.library;

/**
 * @author 潘维吉
 * @date 2025/10/9 9:20
 * @email 406798106@qq.com
 * @description 全局缓存工具类 - 基于文件系统
 */
class GlobalCache implements Serializable  {

    static String CACHE_DIR = "/tmp/jenkins_cache"

   // 设置缓存
    static void set(String key, String value, int ttlMinutes = 60) {
        def cacheFile = new File("${CACHE_DIR}/${key}.cache")
        cacheFile.parentFile.mkdirs()

        def expireTime = System.currentTimeMillis() + (ttlMinutes * 60 * 1000)
        def content = "${expireTime}\n${value}"

        cacheFile.write(content, 'UTF-8')
    }

   // 获取缓存
    static String get(String key) {
        def cacheFile = new File("${CACHE_DIR}/${key}.cache")

        if (!cacheFile.exists()) {
            return null
        }

        def lines = cacheFile.readLines('UTF-8')
        if (lines.size() < 2) {
            return null
        }

        def expireTime = lines[0] as Long
        if (System.currentTimeMillis() > expireTime) {
            cacheFile.delete()
            return null
        }

        return lines[1..-1].join('\n')
    }

   // 删除缓存
    static void delete(String key) {
        def cacheFile = new File("${CACHE_DIR}/${key}.cache")
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }

   // 清理过期缓存
    static void cleanup() {
        def cacheDir = new File(CACHE_DIR)
        if (!cacheDir.exists()) return

        cacheDir.listFiles().each { file ->
            if (file.name.endsWith('.cache')) {
                def lines = file.readLines('UTF-8')
                if (lines.size() > 0) {
                    def expireTime = lines[0] as Long
                    if (System.currentTimeMillis() > expireTime) {
                        file.delete()
                    }
                }
            }
        }
    }

}