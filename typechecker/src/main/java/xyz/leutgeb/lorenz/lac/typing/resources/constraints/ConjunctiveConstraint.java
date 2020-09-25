package xyz.leutgeb.lorenz.lac.typing.resources.constraints;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.BiMap;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.RealExpr;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Node;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import xyz.leutgeb.lorenz.lac.typing.resources.coefficients.Coefficient;

@Value
public class ConjunctiveConstraint extends Constraint {
  List<Constraint> elements;

  public ConjunctiveConstraint(List<Constraint> elements, String reason) {
    super(reason);
    Objects.requireNonNull(elements);
    if (elements.isEmpty()) {
      throw new IllegalArgumentException("elements cannot be empty");
    }
    this.elements = elements;
  }

  @Override
  public BoolExpr encode(Context ctx, BiMap<Coefficient, RealExpr> coefficients) {
    return ctx.mkAnd(
        elements.stream()
            .map(element -> element.encode(ctx, coefficients))
            .toArray(BoolExpr[]::new));
  }

  @Override
  public Graph toGraph(Graph graph, Map<Coefficient, Node> nodes) {
    throw new UnsupportedOperationException("cannot convert conjunctive constraint to graph");
  }

  @Override
  public Constraint replace(Coefficient target, Coefficient replacement) {
    return new ConjunctiveConstraint(
        elements.stream().map(element -> element.replace(target, replacement)).collect(toList()),
        getReason());
  }

  @Override
  public Set<Coefficient> occurringCoefficients() {
    return elements.stream()
        .map(Constraint::occurringCoefficients)
        .flatMap(Set::stream)
        .collect(toSet());
  }

  @Override
  public String toString() {
    if (elements.isEmpty()) {
      return "true";
    }
    return elements.stream().map(Object::toString).collect(Collectors.joining(" ∧ ", "(", ")"));
  }

  @Override
  public Stream<Constraint> children() {
    return elements.stream();
  }
}