package kz.hxncus.mc.dynamicshop;
import kz.hxncus.mc.dynamicshop.domain.Item;
import kz.hxncus.mc.dynamicshop.domain.Shop;
import org.bukkit.Material;

import java.sql.*;
import java.util.*;

public class ShopRepository {
    private final String url = "";
    private final String user = "";
    private final String password = "";

    private Connection connection;

    public ShopRepository() {
    }

    private Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                return DriverManager.getConnection(url, user, password);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    public Optional<Shop> findById(String id) {
        String shopSql = "SELECT * FROM shops WHERE id = ?";
        String itemsSql = "SELECT * FROM items WHERE shop_id = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(shopSql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }
            List<Item> items = new ArrayList<>();
            try (PreparedStatement psItems = con.prepareStatement(itemsSql)) {
                psItems.setString(1, id);
                ResultSet rsItems = psItems.executeQuery();

                while (rsItems.next()) {
                    items.add(new Item(
                            Material.getMaterial(rsItems.getString("type")),
                            rsItems.getDouble("price")
                    ));
                }
            }
            Shop shop = new Shop(id, items);
            return Optional.of(shop);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Shop> findAll() {
        String sql = "SELECT s.id AS shop_id, i.type, i.price " +
                "FROM shops s " +
                "LEFT JOIN items i ON s.id = i.shop_id";

        Map<String, List<Item>> shopItemsMap = new LinkedHashMap<>();

        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String shopId = rs.getString("shop_id");

                shopItemsMap.putIfAbsent(shopId, new ArrayList<>());

                String materialName = rs.getString("type");
                if (materialName != null) {
                    Material material = Material.valueOf(materialName);
                    double price = rs.getDouble("price");

                    shopItemsMap.get(shopId).add(new Item(material, price));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException();
        }

        List<Shop> shops = new ArrayList<>();
        for (Map.Entry<String, List<Item>> entry : shopItemsMap.entrySet()) {
            shops.add(new Shop(entry.getKey(), entry.getValue()));
        }

        return shops;
    }
    public void save(Shop shop) {
        String saveShopSql = "INSERT INTO shops (id) VALUES (?) ON DUPLICATE KEY UPDATE id = id";
        String deleteItemsSql = "DELETE FROM items WHERE shop_id = ?";
        String insertItemSql = "INSERT INTO items (shop_id, type, price) VALUES (?, ?, ?)";

        Connection con = null;
        try {
            con = getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(saveShopSql)) {
                ps.setString(1, shop.getId());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(deleteItemsSql)) {
                ps.setString(1, shop.getId());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(insertItemSql)) {
                for (Item item : shop.getItems()) {
                    ps.setString(1, shop.getId());
                    ps.setString(2, item.getType().name());
                    ps.setDouble(3, item.getPrice());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            con.commit();

        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new RuntimeException();
        } finally {
            if (con != null) {
                try { con.setAutoCommit(true); con.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }
}
