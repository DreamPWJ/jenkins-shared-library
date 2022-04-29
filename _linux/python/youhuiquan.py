#!/usr/bin/python
# -*- coding: UTF-8 -*-
"""
 Python执行SQL
"""
import pymysql
import traceback
import logging


def get_logger():
    """
    创建日志实例
    """
    logger_ = logging.getLogger(__name__)
    handler = logging.FileHandler(filename='/home/tasks/{}.log'.format('info'), mode='a')
    formatter = logging.Formatter('%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s %(message)s',
                                  datefmt='%a, %d %b %Y %H:%M:%S')
    handler.setFormatter(formatter)
    logger_.addHandler(handler)
    logger_.setLevel(logging.INFO)
    return logger_


logger = get_logger()


def run():
    try:
        logger.info('执行定时任务。。。。。。。。。。')
        # 创建连接
        conn = pymysql.connect(host='172.16.99.11', port=3306, user='root', passwd='rzzhtc', db='epark', charset='utf8')
        # 创建游标
        cursor = conn.cursor()
        # 执行SQL，并返回收影响行数
        sql_str = """
        INSERT INTO info_park_card (nid, remain_money, begin_time, end_time, leaguer_id, bind_time,
                                               amount, valid_scope, third_valid_scope)
         (select REPLACE(UUID(), '-', ''),
                       case
                           when ilrr.recharge_fee >= 50 and ilrr.recharge_fee < 100 then 8
                           when ilrr.recharge_fee >= 100 and ilrr.recharge_fee < 200 then 20
                           when ilrr.recharge_fee >= 200 then 56 end as money,
                       now(),
                       '2022-12-31 23:59:59',
                       il.leaguer_id,
                       now(),
                       case
                           when ilrr.recharge_fee >= 50 and ilrr.recharge_fee < 100 then 8
                           when ilrr.recharge_fee >= 100 and ilrr.recharge_fee < 200 then 20
                           when ilrr.recharge_fee >= 200 then 56 end as money,
                       '-1',
                       '-1'
                from info_leaguer il
                         inner join info_leaguer_recharge_record ilrr on il.leaguer_id = ilrr.leaguer_id
                where ilrr.recharge_type = '9701'
                  and ilrr.recharge_fee >= 50
                  and ilrr.recharge_time between (select if(now() <= '2022-04-30 00:00:00', '2022-04-30 00:00:00', date_add(date_sub(now(), interval 1 hour), interval 1 second))) and '2022-05-31 23:59:59');
        """
        effect_row = cursor.execute(sql_str)
        logger.info('执行SQL返回影响行数: {}'.format(effect_row))
        conn.commit()
        # 关闭游标
        cursor.close()
        # 关闭连接
        conn.close()
    except:
        logger.error("定时任务执行异常。。。。。。。。。。")
        logger.error(traceback.format_exc())


if __name__ == '__main__':
    run()


# 查看Python版本   python -V
# 升级Python3命令
# yum update -y && yum install -y python3 && python -V
# wget https://www.python.org/ftp/python/3.8.0/Python-3.8.0.tgz && tar -zxvf Python-3.8.0.tgz && cd Python-3.8.0 &&  ./configure -prefix=/usr/local/python3 && make && make install
# 安装pip命令 yum install -y epel-release && yum install -y python-pip && pip install --upgrade pip
# pip3 install PyMySQL
# 0 */1 * * * /usr/bin/python3 /home/tasks/youhuiquan.py >> /home/tasks/youhuiquan.log
# service crond restart , Ubuntu 使用 sudo service cron start  # 重启crond生效
