-- ==================== 1. 验证数据量 ====================
select '设备类型数量' as 统计项, count(*) as 数量 from device_types
union all
select '设备数量', count(*) from devices
union all
select '数据记录数量', count(*) from device_data
union all
select '告警记录数量', count(*) from alerts;

-- ==================== 2. 设备在线率统计 ====================
-- 统计目标：按设备类型统计设备总数、在线数量以及在线率。
-- 统计口径：设备状态 status = 1 表示在线，其他状态均视为非在线。
-- 关联方式：以设备类型表 device_types 为主表，左连接设备表 devices，确保没有设备的类型也能展示。
-- count() 作用：统计指定字段的非空记录数量，这里用于统计每类设备的设备总数。
-- sum() 作用：对数值结果进行求和，这里用于累计在线设备数量。
-- case when 作用：条件判断函数，这里用于判断设备是否在线，在线返回 1，否则返回 0。
-- round() 作用：对数值进行四舍五入，这里用于将在线率保留 1 位小数。
-- concat() 作用：拼接字符串，这里用于将计算出的在线率数值与百分号 % 拼接。
-- nullif() 作用：当两个值相等时返回 null，这里用于避免设备总数为 0 时发生除零错误。
-- coalesce() 作用：返回参数中第一个非 null 值，这里用于将空在线率默认显示为 0.0%。

select dt.type_name                as '设备类型', -- 查询设备类型名称，作为统计分组的展示字段
       count(d.id)                 as '设备总数', -- 统计当前设备类型下的设备总数量，count(d.id) 只统计存在的设备记录
       sum(if(d.status = 1, 1, 0)) as '在线数量', -- 统计在线设备数量，status = 1 时计为 1，否则计为 0
       concat( -- concat()：字符串拼接函数，用于把在线率数值和百分号 '%' 拼接成最终显示结果
               coalesce( -- coalesce()：返回第一个非 null 的值；如果在线率计算结果为 null，则使用默认值 0.0
                       round( -- round()：对在线率计算结果进行四舍五入
                               sum(if(d.status = 1, 1, 0)) -- sum(if())：统计在线设备数量；status = 1 返回 1，否则返回 0，最后求和
                                   / nullif(count(d.id), 0) -- nullif()：如果设备总数 count(d.id) 为 0，则返回 null，避免除以 0
                                   * 100, -- 将在线比例转换为百分比，例如 0.856 转换为 85.6
                               1 -- 保留 1 位小数
                       ),
                       0.0 -- 当 round(...) 的结果为 null 时，默认显示为 0.0
               ),
               '%' -- 百分号字符串，用于拼接成类似 85.6% 的显示格式
       ) as '在线率'                              -- 字段别名：将该计算结果命名为“在线率”
from device_types dt -- 设备类型表，作为主表，用于按设备类型进行统计
         left join
     devices d -- 设备表，提供设备状态、设备类型等明细数据
     on
         dt.id = d.type_id -- 根据设备类型 id 关联设备类型表和设备表
group by dt.id,dt.type_name;-- 按设备类型 id 和设备类型名称分组，分别统计每类设备的在线情况



-- ==================== 3. 最近24小时数据趋势 ====================
select
    d.device_name AS '设备名称',
    count(dd.id) AS '数据条数',
    min(dd.data_value) AS '最小值',
    max(dd.data_value) AS '最大值',
    round(avg(dd.data_value), 2) AS '平均值' -- avg()计算平均值,round四舍五入
from devices d join device_data dd ON d.id = dd.device_id -- 主表为devices设别表，连接查询devices_data数据表,根据设备表中id
where dd.collect_time >= date_sub(now(), interval 24 hour) -- 判断条件为时间是否大于现在时间的24小时前的时间值
group by d.id, d.device_name -- 根据设备id与设备名称排序
order by 数据条数 desc; -- 根据数据条数降序

-- ==================== 4. 未处理告警列表 ====================
select a.id,
       d.device_name   AS '设备名称',
       d.location      AS '设备地点',
       a.alert_type    AS '告警类型',
        case a.alert_level
           when 1 then '一般'
           when 2 then '严重'
           when 3 then '紧急'
           end         AS '告警级别',
       a.alert_message AS '告警信息',
       a.create_time   AS '告警时间'

from alerts a
         join devices d on a.device_id = d.id
where a.is_handled = 0
order by a.alert_level desc, a.create_time desc;

-- ==================== 5. 验证外键关系 ====================
-- 查询每个设备的类型信息（验证一对多）
select d.device_name,
       d.location,
       dt.type_name,
       dt.description
from devices d
         left join device_types dt on d.type_id = dt.id
limit 5;


