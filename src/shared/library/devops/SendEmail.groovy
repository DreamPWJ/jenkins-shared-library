package shared.library.devops

/**
 * @author 潘维吉
 * @date 2021/1/20 22:29
 * @email 406798106@qq.com
 * @description 发送邮件
 */

/**
 *  发送邮件
 */
def sendEmail(email, subject, body) {
    mail to: email,
            subject: subject,
            body: body
}

/**
 *  发送邮件 定制邮件内容
 */
def sendEmail(status, emailUser) {
    emailext body: """
            <!DOCTYPE html> 
            <html> 
            <head> 
            <meta charset="UTF-8"> 
            </head> 
            <body leftmargin="8" marginwidth="0" topmargin="8" marginheight="4" offset="0"> 
                <table width="95%" cellpadding="0" cellspacing="0" style="font-size: 11pt; font-family: Tahoma, Arial, Helvetica, sans-serif">   
                <tr>
                    本邮件由系统自动发出，无需回复！<br/>
                    各位同事，大家好，以下为${JOB_NAME}项目构建信息</br>
                    <td><font color="#CC0000">构建结果 - ${status}</font></td>
                </tr>

                    <tr> 
                        <td><br /> 
                            <b><font color="#0B610B">构建信息</font></b> 
                        </td> 
                    </tr> 
                    <tr> 
                        <td> 
                            <ul> 
                                <li>项目名称: ${JOB_NAME}</li>         
                                <li>构建编号: ${BUILD_ID}</li> 
                                <li>构建状态: ${status} </li>                         
                                <li>项目地址: <a href="${BUILD_URL}">${BUILD_URL}</a></li>    
                                <li>构建日志: <a href="${BUILD_URL}console">${BUILD_URL}console</a></li>   
                            </ul> 
                        </td> 
                    </tr> 
                    <tr>  
                </table> 
            </body> 
            </html>  """,
            subject: "Jenkins-${JOB_NAME}项目构建信息 ",
            to: emailUser
}
