package com.roguelike.dungeon.game.character;

import java.util.List;

/**
 * 角色目录查询接口（《主菜单与角色选择接口约定》v1.0 第 5 节）。
 *
 * <p>角色负责人（王佳一）通过实现本接口提供可选角色数据；主菜单不允许用
 * {@code if/else} 写死具体角色，只通过本接口查询。</p>
 */
public interface CharacterCatalog {

    /**
     * 返回当前可选角色，只读且不含 {@code null}，顺序即选角页默认展示顺序。
     */
    List<CharacterDefinition> getAvailableCharacters();

    /**
     * 按稳定角色编号查询。
     *
     * @param characterId 角色编号
     * @return 匹配的角色定义
     * @throws IllegalArgumentException 角色编号不存在（错误信息包含传入的编号）
     */
    CharacterDefinition getById(String characterId);
}
