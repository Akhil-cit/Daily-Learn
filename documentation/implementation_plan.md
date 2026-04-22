# AWS Free Tier Deployment Plan ☁️

Goal: Migrate the "Daily Learn" backend from a local XAMPP/ngrok setup to a permanent, cloud-hosted environment on AWS using the Free Tier.

## User Review Required

> [!IMPORTANT]
> You must have an active **AWS Account**. If you don't have one, please create it at [aws.amazon.com](https://aws.amazon.com/). You will need a credit/debit card for verification (AWS will not charge you as long as we stay within Free Tier limits).

> [!WARNING]
> We will be moving from `http` to a public IP. For full production, an SSL certificate (https) is recommended, but for this version, we will use the Public IP to keep things simple.

## Proposed Steps

### Phase 1: Database Setup (AWS RDS)
1. **Create RDS Instance**: 
   - Launch a MySQL `t3.micro` or `t2.micro` instance.
   - Set the Master Username and Password (we can use the same as your `.env` for consistency).
   - **Important**: Enable "Public Access" so we can initialize the tables from your computer.
2. **Connectivity**: 
   - Update the RDS Security Group to allow inbound traffic on port `3306`.
3. **Migration**:
   - Update your local `.env` with the new RDS Endpoint.
   - Run a migration script (or just restart the Flask app) to create the tables on RDS.

---

### Phase 2: Server Setup (AWS EC2)
1. **Launch EC2 Instance**:
   - Choose **Ubuntu 22.04 LTS** (Free Tier eligible).
   - Use `t2.micro` or `t3.micro`.
   - Create and download a **Key Pair (.pem file)** for SSH access.
2. **Security Group Configuration**:
   - Open Port **22** (SSH).
   - Open Port **5000** (Flask API).
3. **Elastic IP**:
   - Allocate and associate an Elastic IP to your instance so the address doesn't change when the server restarts.

---

### Phase 3: Deployment
1. **Connect via SSH**:
   - Use PowerShell or PuTTY to log into your EC2 instance.
2. **Environment Setup**:
   - Install Python, pip, and Git on the server.
   - Clone your repository: `git clone https://github.com/Akhil-cit/Daily-Learn.git`
3. **Run the App**:
   - Create the `.env` file on the server with RDS credentials.
   - Install dependencies: `pip install -r requirements.txt`.
   - Start the server using `gunicorn` or `pm2` so it runs in the background.

---

### Phase 4: Android App Update
1. **Update MainActivity**:
   - Replace the `ngrokUrl` with your **AWS Elastic IP**: `http://3.xxx.xxx.xxx:5000`.
2. **Test Flow**:
   - Verify that Login, Signup, and Syllabus Upload work correctly with the cloud database.

## Verification Plan

### Manual Verification
- [ ] Connect to RDS using MySQL Workbench or CLI.
- [ ] Access the Flask health check or `/` route via the EC2 Public IP in a browser.
- [ ] Perform a full end-to-end test from the Android app.
