# 主子记录列表一条sql实现写法
select bp.id,
       bp.content,
       CONCAT('[', convert(GROUP_CONCAT(
               distinct JSON_OBJECT('id', bpc.id, 'content', bpc.content) ORDER BY bpc.id ASC), char), ']') as items
from bbs_post bp
         inner join (select * from bbs_post_comment where post_id = 56 order by ct_date desc limit 2) bpc
                    on bp.id = bpc.post_id
group by bp.id;

# 主子记录列表一条sql实现写法 去掉JSON_OBJECT内的null值
select hp.id,
       hp.name,
       CONCAT('[', IF(any_value(ha.id) IS NULL,
                      '', convert(GROUP_CONCAT(
                       distinct JSON_OBJECT('id', ha.id, 'name', ha.name) ORDER BY ha.id ASC), char)), ']') as items
from house_property hp
         left join hp_periphery_support_rel hpsr on hp.id = hpsr.house_property_id
         left join hp_periphery_support ha on hpsr.periphery_support_id = hpsr.id
group by hp.id;

# CTE递归查询所有的子节点 并查询是否是叶子节点字段
with recursive temp as (
    select t1.*
    from sys_business t1
    where t1.id = 1
    union all
    select t2.*
    from temp,
         sys_business t2
    where temp.id = t2.parent_id
)
select distinct case
                    when exists(select 1 from temp c1 where t1.id = c1.parent_id)
                        then 'false'
                    else 'true' end as is_leaf,
                t1.*
from temp t1;


# CTE递归查询所有的父节点
with recursive temp as (
    select t1.*
    from sys_business t1
    where t1.id = 2
    union all
    select t2.*
    from temp,
         sys_business t2
    where temp.parent_id = t2.id
)
select distinct *
from temp;

# 查询所有的叶子结点
select b.*
from sys_business a
         right join sys_business b
                    on a.parent_id = b.id
group by b.id
having count(a.id) = 0;

# 查询子节点的所有父节点一条sql实现写法 基于ids_path字段值 ids_path like 可查询子节点
select *
from dual
where find_in_set(id
          , (select GROUP_CONCAT(distinct replace('1/2', '/', ',')) from dual));


# Mybatis like查询的写法
# select * from community where name like "%"#{name}"%"

# MySQL写的顺序: select ... from... where.... group by... having... order by... limit [offset,] (rows)
# MySQL执行顺序: from... where...group by... having.... select ... order by... limit
