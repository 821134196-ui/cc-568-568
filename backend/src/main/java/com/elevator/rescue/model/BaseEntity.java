package com.elevator.rescue.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * 主键基类。
 * 使用 IDENTITY（SQLite rowid 自增）而非 Hibernate 默认表序列：
 * 表序列生成器会在事务内再申请一个"隔离连接"，在单连接 SQLite 池下自死锁。
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
}
