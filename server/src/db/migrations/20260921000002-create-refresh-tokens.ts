import { DataTypes, type QueryInterface } from 'sequelize';

export async function up(queryInterface: QueryInterface): Promise<void> {
  await queryInterface.createTable('refresh_tokens', {
    id: { type: DataTypes.BIGINT.UNSIGNED, autoIncrement: true, primaryKey: true },
    user_id: {
      type: DataTypes.BIGINT.UNSIGNED,
      allowNull: false,
      references: { model: 'users', key: 'id' },
      onDelete: 'CASCADE',
    },
    // SHA-256 of the raw token. The raw token itself is never stored.
    token_hash: { type: DataTypes.CHAR(64), allowNull: false, unique: true },
    // All tokens created by rotating one login share a family; reuse of a rotated token revokes the family.
    family_id: { type: DataTypes.CHAR(36), allowNull: false },
    expires_at: { type: DataTypes.DATE(3), allowNull: false },
    revoked_at: { type: DataTypes.DATE(3), allowNull: true },
    ip: { type: DataTypes.STRING(45), allowNull: true },
    user_agent: { type: DataTypes.STRING(255), allowNull: true },
    created_at: { type: DataTypes.DATE(3), allowNull: false },
  });
  await queryInterface.addIndex('refresh_tokens', ['user_id'], { name: 'refresh_tokens_user_idx' });
  await queryInterface.addIndex('refresh_tokens', ['family_id'], { name: 'refresh_tokens_family_idx' });
  await queryInterface.addIndex('refresh_tokens', ['expires_at'], { name: 'refresh_tokens_expires_idx' });
}

export async function down(queryInterface: QueryInterface): Promise<void> {
  await queryInterface.dropTable('refresh_tokens');
}
