package xyz.leutgeb.lorenz.logs.ast;

import lombok.Data;

@Data
public class Case extends Expression {
  private final Pattern matcher;
  private final Expression body;
}
