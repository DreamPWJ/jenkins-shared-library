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
        conn = pymysql.connect(host='172.16.0.208', port=3306, user='root', passwd='lanneng2020', db='epark', charset='utf8')
        # 创建游标
        cursor = conn.cursor()
        # 执行SQL，并返回收影响行数
        sql_str = """
            INSERT INTO info_park_card (nid, remain_money, begin_time, end_time, leaguer_id, bind_time,
                                amount, valid_scope, third_valid_scope)
        (select uuid_short(),
                20.00,
                now(),
                '2022-05-31 23:59:59',
                il.leaguer_id,
                now(),
                20.00,
                '-1',
                '-1'
         from info_leaguer il
                  inner join info_leaguer_plate ilp on
             il.leaguer_id = ilp.leaguer_id
         where il.leaguer_id not in (select info_park_card.leaguer_id from info_park_card)
           and il.is_lock = 0102
           and ilp.bind_state = 2901
           and ilp.insert_time between
             (select ipc.bind_time from info_park_card ipc order by bind_time desc limit 1)
             and '2022-04-30 23:59:59'
         group by il.leaguer_id);
        """
        effect_row = cursor.execute(sql_str)
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

