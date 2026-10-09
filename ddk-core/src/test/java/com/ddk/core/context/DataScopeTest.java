package com.ddk.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataScope：操作者的数据范围")
class DataScopeTest {

    @Test
    @DisplayName("四种基本范围")
    void basicScopes() {
        assertThat(DataScope.all().unrestricted()).isTrue();
        assertThat(DataScope.ofGroups(List.of("10", "20"))).isEqualTo(new DataScope(false, java.util.Set.of("10", "20"), false));
        assertThat(DataScope.ownOnly()).isEqualTo(new DataScope(false, java.util.Set.of(), true));
        assertThat(DataScope.none()).isEqualTo(new DataScope(false, java.util.Set.of(), false));
    }

    @Test
    @DisplayName("andOwn 在原有范围之外加上本人的数据，不改变原对象")
    void andOwnAddsTheOperatorsOwnData() {
        DataScope groups = DataScope.ofGroups(List.of("10"));

        DataScope widened = groups.andOwn();

        assertThat(widened.own()).isTrue();
        assertThat(widened.groups()).containsExactly("10");
        assertThat(groups.own()).isFalse();
    }

    @Test
    @DisplayName("组的集合是不可变的副本")
    void groupsAreAnImmutableCopy() {
        List<String> source = new ArrayList<>(List.of("10"));
        DataScope scope = DataScope.ofGroups(source);
        source.add("20");

        assertThat(scope.groups()).containsExactly("10");
        assertThatThrownBy(() -> scope.groups().add("30")).isInstanceOf(UnsupportedOperationException.class);
    }
}
