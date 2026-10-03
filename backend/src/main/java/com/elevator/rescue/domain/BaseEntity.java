package com.elevator.rescue.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * 实体主键基类：使用 SQLite 自增主键（rowid）。
 * 不使用 PanacheEntity 默认的 TABLE 序列——它会在独立连接上读写 *_SEQ 表，
 * 与单连接池冲突导致启动死锁。
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
}
