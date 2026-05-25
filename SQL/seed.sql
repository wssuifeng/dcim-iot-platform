-- ==================== 插入设备类型 ====================
INSERT INTO device_types (type_name, type_code, description)
VALUES ('温度传感器', 'TEMP', '用于测量环境温度'),
       ('湿度传感器', 'HUMIDITY', '用于测量环境湿度'),
       ('烟雾传感器', 'SMOKE', '用于检测烟雾浓度'),
       ('门禁设备', 'DOOR', '用于门禁管理');

-- ==================== 插入设备信息 ====================
INSERT INTO devices (device_name, device_code, type_id, status, location, ip_address)
VALUES ('温度传感器01', 'TEMP-001', 1, 1, '1号车间', '192.168.1.101'),
       ('温度传感器02', 'TEMP-002', 1, 1, '2号车间', '192.168.1.102'),
       ('湿度传感器01', 'HUMIDITY-001', 2, 1, '1号车间', '192.168.1.103'),
       ('湿度传感器02', 'HUMIDITY-002', 2, 0, '2号车间', '192.168.1.104'),
       ('烟雾传感器01', 'SMOKE-001', 3, 1, '1号车间', '192.168.1.105'),
       ('烟雾传感器02', 'SMOKE-002', 3, 2, '仓库', '192.168.1.106'),
       ('门禁设备01', 'DOOR-001', 4, 1, '大门', '192.168.1.107'),
       ('门禁设备02', 'DOOR-002', 4, 1, '后门', '192.168.1.108'),
       ('温度传感器03', 'TEMP-003', 1, 0, '办公室', '192.168.1.109'),
       ('湿度传感器03', 'HUMIDITY-003', 2, 1, '会议室', '192.168.1.110');

-- ==================== 插入设备数据（最近24小时） ====================
-- 【整体思路】自动生成50条测试数据。
-- 因为要用到循环和变量，所以写成一个“存储过程”（一小段临时程序），
-- 运行完毕后自动删除，不影响数据库。

-- 1. 临时把结束符从 ; 改为 //，防止程序内部的 ; 被误认为是语句结束
delimiter //

create procedure insert_device_data()
begin
    declare i int default 1;         -- 声明计数器，从1开始
    declare device_id int;           -- 声明变量：设备ID
    declare data_val decimal(10, 2); -- 声明变量：数据值，10是总位数，2是小数位数,例如,12345678.00

    while i <= 50 do                 -- 循环50次，插入50条数据

    -- 1. 随机选一个设备 (1-10之间)
    --    rand()生成0~1的小数，*10得0~9.999，+1得1~10.999，floor()向下取整得1~10的整数
            set device_id = floor(1 + rand() * 10);

            -- 2. 随机生成温度值 (20℃ ~ 50℃之间，保留两位小数)
            --    20 + rand()*30 得到 20~49.999，round(..., 2) 四舍五入保留2位小数
            set data_val = round(20 + rand() * 30, 2);

            -- 3. 插入数据
            --    date_sub(现在时间, interval 随机分钟数 minute) = 往前推随机时间
            --    rand()*1440 产生 0~1439 的随机数（24小时=1440分钟），模拟过去24小时内的时间
            insert into device_data (device_id, data_value, data_unit, collect_time)
            values (device_id, data_val, '℃',
                    date_sub(now(), interval floor(rand() * 1440) minute));
            -- date_sub(now(), interval floor(rand() * 1440) minute)在当前时间的基础上随机减去0~1439分钟，得到一个过去24小时内的随机时间
            set i = i + 1;  -- 计数器+1，准备下一次循环
        end while;
end //
delimiter ;  -- 4. 恢复默认结束符为 ;

-- 执行这段程序
call insert_device_data();

-- 用完即删，保持数据库整洁
drop procedure insert_device_data;


-- ==================== 插入告警记录 ====================
INSERT INTO alerts (device_id, alert_type, alert_level, alert_message, is_handled)
VALUES (6, '设备故障', 3, '烟雾传感器02通信中断，请检查设备', 0),
       (3, '数据异常', 2, '湿度传感器01数据波动异常', 1),
       (1, '温度告警', 2, '温度传感器01温度超过阈值', 0),
       (5, '低电量', 1, '烟雾传感器01电量低于20%', 0),
       (9, '离线告警', 1, '温度传感器03已离线超过24小时', 1);