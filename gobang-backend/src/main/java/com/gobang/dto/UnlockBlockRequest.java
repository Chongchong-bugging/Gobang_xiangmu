package com.gobang.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UnlockBlockRequest {

    @NotNull(message = "区块行不能为空")
    @Min(value = 0, message = "区块行不能小于0")
    @Max(value = 4, message = "区块行不能大于4")
    private Integer blockRow;

    @NotNull(message = "区块列不能为空")
    @Min(value = 0, message = "区块列不能小于0")
    @Max(value = 4, message = "区块列不能大于4")
    private Integer blockCol;

    public Integer getBlockRow() { return blockRow; }
    public void setBlockRow(Integer blockRow) { this.blockRow = blockRow; }
    public Integer getBlockCol() { return blockCol; }
    public void setBlockCol(Integer blockCol) { this.blockCol = blockCol; }
}
