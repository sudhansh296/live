import { DataTypes, type QueryInterface } from 'sequelize';

export async function up(queryInterface: QueryInterface): Promise<void> {
  await queryInterface.createTable('users', {
    id: { type: DataTypes.BIGINT.UNSIGNED, autoIncrement: true, primaryKey: true },
    // The only id ever sent to clients (never the auto-increment id).
    public_id: { type: DataTypes.CHAR(36), allowNull: false, unique: true },
    firebase_uid: { type: DataTypes.STRING(128), allowNull: false, unique: true },
    phone_e164: { type: DataTypes.STRING(20), allowNull: true, unique: true },
    email: { type: DataTypes.STRING(255), allowNull: true },
    email_verified: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
    sign_in_provider: { type: DataTypes.STRING(40), allowNull: true },
    status: { type: DataTypes.ENUM('active', 'suspended', 'deleted'), allowNull: false, defaultValue: 'active' },
    terms_version: { type: DataTypes.STRING(20), allowNull: true },
    terms_accepted_at: { type: DataTypes.DATE(3), allowNull: true },
    profile_completed_at: { type: DataTypes.DATE(3), allowNull: true },
    last_login_at: { type: DataTypes.DATE(3), allowNull: true },
    created_at: { type: DataTypes.DATE(3), allowNull: false },
    updated_at: { type: DataTypes.DATE(3), allowNull: false },
  });
  await queryInterface.addIndex('users', ['email'], { name: 'users_email_idx' });
  await queryInterface.addIndex('users', ['status'], { name: 'users_status_idx' });
}

export async function down(queryInterface: QueryInterface): Promise<void> {
  await queryInterface.dropTable('users');
}
