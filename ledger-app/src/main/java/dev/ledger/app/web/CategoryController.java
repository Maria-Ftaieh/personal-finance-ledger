package dev.ledger.app.web;

import dev.ledger.app.domain.SubcategoryEntity;
import dev.ledger.app.repo.CategoryRepository;
import dev.ledger.app.repo.SubcategoryRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryRepository categories;
  private final SubcategoryRepository subcategories;

  public CategoryController(CategoryRepository categories, SubcategoryRepository subcategories) {
    this.categories = categories;
    this.subcategories = subcategories;
  }

  /** The whole two-level taxonomy in one call; it is small and the UI needs all of it. */
  @GetMapping
  public List<CategoryView> tree() {
    Map<String, List<SubcategoryEntity>> byCategory =
        subcategories.findAllByOrderByCategoryIdAscSortOrderAsc().stream()
            .collect(Collectors.groupingBy(SubcategoryEntity::getCategoryId));

    return categories.findAllByOrderBySortOrderAsc().stream()
        .map(
            category ->
                new CategoryView(
                    category.getId(),
                    category.getDisplayName(),
                    category.isSystem(),
                    byCategory.getOrDefault(category.getId(), List.of()).stream()
                        .map(sub -> new SubcategoryView(sub.getId(), sub.getDisplayName()))
                        .toList()))
        .toList();
  }

  /**
   * @param displayName Turkish; the frontend routes it through its i18n layer (SPEC §0).
   */
  public record CategoryView(
      String id, String displayName, boolean system, List<SubcategoryView> subcategories) {}

  public record SubcategoryView(String id, String displayName) {}
}
