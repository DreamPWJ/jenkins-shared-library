package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/4/16 13:22
 * @email 406798106@qq.com
 * @description 数据库相关
 */
class Database implements Serializable {

    /**
     * 执行数据库sql
     */
    static def runSql(ctx, dbHost, dbUser, dbName, sqlCmd) {
        // 使用 def res = runSql(this, dbHost, dbUser, dbName, sqlCmd)
        ctx.sh(returnStdout: true, script: "mysql -h$dbHost -u$dbUser -p123 $dbName  -e \"$sqlCmd;\"")
    }

}
