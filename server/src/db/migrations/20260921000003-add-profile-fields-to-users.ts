import { DataTypes, type QueryInterface } from 'sequelize';

export async function up(queryInterface: QueryInterface): Promise<void> {
  await queryInterface.addColumn('users', 'display_name', { type: DataTypes.STRING(30), allowNull: true });
  // Stored lowercase. The column collation is case-insensitive, so the unique index also blocks "Sudha" vs "sudha".
  await queryInterface.addColumn('users', 'username', { type: DataTypes.STRING(20), allowNull: true });
  // Private: never sent to other users. Only kept so the age check can be audited.
  await queryInterface.addColumn('users', 'birth_date', { type: DataTypes.DATEONLY, allowNull: true });
  await queryInterface.addColumn('users', 'country_code', { type: DataTypes.CHAR(2), allowNull: true });
  // Random file name of the avatar image (never the original upload name).
  await queryInterface.addColumn('users', 'avatar_key', { type: DataTypes.STRING(64), allowNull: true });
  // Set when someone under 18 tried to sign up: that account cannot retry with another birth date.
  await queryInterface.addColumn('users', 'age_check_failed_at', { type: DataTypes.DATE(3), allowNull: true });
  await queryInterface.addIndex('users', ['username'], { name: 'users_username_uq', unique: true });
}

export async function down(queryInterface: QueryInterface): Promise<void> {
  await queryInterface.removeIndex('users', 'users_username_uq');
  await queryInterface.removeColumn('users', 'age_check_failed_at');
  await queryInterface.removeColumn('users', 'avatar_key');
  await queryInterface.removeColumn('users', 'country_code');
  await queryInterface.removeColumn('users', 'birth_date');
  await queryInterface.removeColumn('users', 'username');
  await queryInterface.removeColumn('users', 'display_name');
}
