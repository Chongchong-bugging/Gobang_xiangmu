package com.gobang.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class MoveRequest {

    @NotNull(message = "落子行不能为空")
    @Min(value = 0, message = "落子行不能小于0")
    @Max(value = 24, message = "落子行不能大于24")
    private Integer row;

    @NotNull(message = "落子列不能为空")
    @Min(value = 0, message = "落子列不能小于0")
    @Max(value = 24, message = "落子列不能大于24")
    private Integer col;

    public Integer getRow() { return row; }
    public void setRow(Integer row) { this.row = row; }
    public Integer getCol() { return col; }
    public void setCol(Integer col) { this.col = col; }
}
