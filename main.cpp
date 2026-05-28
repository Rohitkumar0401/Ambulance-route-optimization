#include <bits/stdc++.h>
using namespace std;

/* ================== STRUCTS ================== */
struct Edge {
    int to;
    double distance;
    double roadCondition;
    bool blocked;
    vector<pair<double,double>> path;
};

struct User {
    string email,password,role;
};

struct Ambulance {
    int id, location;
    bool available;
};

vector<int> hospitals = {5,10,15,20};
vector<Ambulance> ambulances = {{101,0,true},{102,5,true},{103,10,true}};

/* ================= AUTH SYSTEM ================= */
class AuthSystem {
    unordered_map<string,User> users;
public:
    void loadUsers() {
        ifstream file("users.csv");
        if(!file){
            ofstream f("users.csv");
            f<<"email,password,role\n";
            f.close();
            return;
        }
        string line; getline(file,line); // skip header
        while(getline(file,line)){
            stringstream ss(line);
            string email,pass,role;
            getline(ss,email,','); getline(ss,pass,','); getline(ss,role);
            if(!email.empty()) users[email]={email,pass,role};
        }
    }
    void saveUser(string email,string password,string role){
        ofstream file("users.csv", ios::app);
        file<<email<<","<<password<<","<<role<<"\n";
    }
    void registerUser(){
        string email,password;
        cout<<"Enter Email: "; cin>>email;
        if(users.count(email)){ cout<<"User already exists!\n"; return; }
        cout<<"Enter Password: "; cin>>password;
        users[email]={email,password,"USER"};
        saveUser(email,password,"USER");
        cout<<"Registration Successful\n";
    }
    bool login(){
        string email,password;
        cout<<"Enter Email: "; cin>>email;
        cout<<"Enter Password: "; cin>>password;
        if(users.count(email) && users[email].password==password){
            cout<<"Login Successful\n"; return true;
        }
        cout<<"Login Failed\n"; return false;
    }
    bool menu(){
        int ch;
        cout<<"\n1 Login\n2 Register\n3 Exit\nChoice: ";
        cin>>ch;
        if(ch==1) return login();
        if(ch==2){ registerUser(); return false; }
        exit(0);
    }
};

/* ================= GRAPH ================= */
class Graph {
public:
    int V;
    vector<vector<Edge>> adj;
    Graph(int v){ V=v; adj.resize(V); }

    double computeDistance(vector<pair<double,double>>& path){
        double total=0;
        for(int i=1;i<path.size();i++){
            double dx=path[i].first-path[i-1].first;
            double dy=path[i].second-path[i-1].second;
            total+=sqrt(dx*dx+dy*dy);
        }
        return total*100;
    }

    void addEdge(int u,int v,vector<pair<double,double>> path){
        double dist=computeDistance(path);
        double cond=(rand()%100)/100.0;
        bool blocked=(rand()%10==0);
        adj[u].push_back({v,dist,cond,blocked,path});
        adj[v].push_back({u,dist,cond,blocked,path});
    }
};

/* ================= PARSING ================= */
pair<double,double> parsePoint(string s){
    s.erase(remove(s.begin(),s.end(),'('),s.end());
    s.erase(remove(s.begin(),s.end(),')'),s.end());
    stringstream ss(s); double a,b; char c; ss>>a>>c>>b;
    return {a,b};
}
vector<string> split(string s,char d){
    vector<string> v; string t; stringstream ss(s);
    while(getline(ss,t,d)) v.push_back(t);
    return v;
}
void loadRoads(string file,Graph &g){
    ifstream f(file);
    if(!f){ cout<<"road_shape.csv not found\n"; return; }
    string line; getline(f,line); // header
    while(getline(f,line)){
        if(line.empty()) continue;
        stringstream ss(line);
        string a,b,c;
        getline(ss,a,','); getline(ss,b,','); getline(ss,c);
        try{
            int u=stoi(a),v=stoi(b);
            vector<string> pts=split(c,';'); vector<pair<double,double>> path;
            for(auto &x:pts) path.push_back(parsePoint(x));
            g.addEdge(u,v,path);
        }catch(...){ continue; }
    }
}

/* ================= DIJKSTRA MODULES ================= */
vector<double> dijkstraRiskAvoid(Graph &g,int src,vector<int>& par){
    vector<double> d(g.V,1e9);
    priority_queue<pair<double,int>,vector<pair<double,int>>,greater<>> pq;
    d[src]=0; pq.push({0,src}); par[src]=-1;
    while(!pq.empty()){
        auto [cd,u]=pq.top(); pq.pop();
        for(auto &e:g.adj[u]){
            if(e.blocked) continue;
            double cost=e.distance+e.roadCondition*20;
            if(d[u]+cost<d[e.to]){ d[e.to]=d[u]+cost; par[e.to]=u; pq.push({d[e.to],e.to}); }
        }
    }
    return d;
}

vector<double> dijkstraHybrid(Graph &g,int src,vector<int>& par){
    if(rand()%2) return dijkstraRiskAvoid(g,src,par);
    vector<double> d(g.V,1e9);
    priority_queue<pair<double,int>,vector<pair<double,int>>,greater<>> pq;
    d[src]=0; pq.push({0,src}); par[src]=-1;
    while(!pq.empty()){
        auto [cd,u]=pq.top(); pq.pop();
        for(auto &e:g.adj[u]){
            double cost = e.distance + (rand()%50);
            if(d[u]+cost<d[e.to]){ d[e.to]=d[u]+cost; par[e.to]=u; pq.push({d[e.to],e.to}); }
        }
    }
    return d;
}

vector<double> dijkstraEmergencyFast(Graph &g,int src,vector<int>& par){
    vector<double> d(g.V,1e9);
    priority_queue<pair<double,int>,vector<pair<double,int>>,greater<>> pq;
    d[src]=0; pq.push({0,src}); par[src]=-1;
    while(!pq.empty()){
        auto [cd,u]=pq.top(); pq.pop();
        for(auto &e:g.adj[u]){
            double cost=e.blocked?1e9:e.distance;
            if(d[u]+cost<d[e.to]){ d[e.to]=d[u]+cost; par[e.to]=u; pq.push({d[e.to],e.to}); }
        }
    }
    return d;
}

vector<double> dijkstraWeatherAdaptive(Graph &g,int src,vector<int>& par){
    vector<double> d(g.V,1e9);
    priority_queue<pair<double,int>,vector<pair<double,int>>,greater<>> pq;
    d[src]=0; pq.push({0,src}); par[src]=-1;
    while(!pq.empty()){
        auto [cd,u]=pq.top(); pq.pop();
        for(auto &e:g.adj[u]){
            double cost=e.distance + (rand()%30);
            if(d[u]+cost<d[e.to]){ d[e.to]=d[u]+cost; par[e.to]=u; pq.push({d[e.to],e.to}); }
        }
    }
    return d;
}

vector<double> dijkstraFuelEfficient(Graph &g,int src,vector<int>& par){
    vector<double> d(g.V,1e9);
    priority_queue<pair<double,int>,vector<pair<double,int>>,greater<>> pq;
    d[src]=0; pq.push({0,src}); par[src]=-1;
    while(!pq.empty()){
        auto [cd,u]=pq.top(); pq.pop();
        for(auto &e:g.adj[u]){
            double cost=e.distance*(1+e.roadCondition);
            if(d[u]+cost<d[e.to]){ d[e.to]=d[u]+cost; par[e.to]=u; pq.push({d[e.to],e.to}); }
        }
    }
    return d;
}

/* ================= EMERGENCY ================= */
bool isBadRoad(double cond,bool blocked,double threshold){ return cond>=threshold || blocked; }

void checkEmergency(Graph &g, vector<int>& path,double threshold){
    cout<<"\n===== EMERGENCY CHECK =====\n";
    for(int i=1;i<path.size();i++){
        int u=path[i-1], v=path[i];
        for(auto &e:g.adj[u]){
            if(e.to==v){
                cout<<"Road "<<u<<"->"<<v<<" Condition: "<<e.roadCondition;
                if(isBadRoad(e.roadCondition,e.blocked,threshold)) cout<<" [BAD]";
                cout<<"\n"; break;
            }
        }
    }
}

int severityLevel(Graph &g, vector<int>& path){
    int level=0;
    for(int i=1;i<path.size();i++){
        int u=path[i-1],v=path[i];
        for(auto &e:g.adj[u]){
            if(e.to==v){
                if(e.blocked) level+=3;
                else if(e.roadCondition>0.7) level+=2;
                else level+=1;
            }
        }
    }
    return level;
}

void alertSystem(int level){
    if(level>10) cout<<"CRITICAL EMERGENCY\n";
    else if(level>5) cout<<"HIGH ALERT\n";
    else cout<<"NORMAL\n";
}

bool needReroute(int level){ return level>8; }
void sendEmailAlert(){ cout<<"EMAIL SENT TO AUTHORITY: BAD ROAD CONDITION\n"; }
double totalCost(Graph &g, vector<int>& path){
    double total=0;
    for(int i=1;i<path.size();i++){
        int u=path[i-1],v=path[i];
        for(auto &e:g.adj[u]){
            if(e.to==v) total+=e.distance + e.roadCondition*20;
        }
    }
    return total;
}

/* ================= UTIL ================= */
int closestHospital(Graph &g,int patientLoc){
    int best=-1; double minDist=1e9;
    for(int h:hospitals){
        vector<int> par(g.V);
        auto dist=dijkstraRiskAvoid(g,patientLoc,par);
        if(dist[h]<minDist){ minDist=dist[h]; best=h; }
    }
    return best;
}

int closestAmbulance(int patientLoc){
    int best=-1; double minDist=1e9;
    for(auto &a:ambulances){
        if(!a.available) continue;
        double dist=abs(a.location-patientLoc);
        if(dist<minDist){ minDist=dist; best=a.id; }
    }
    return best;
}

/* ================= MAIN ================= */
int main(){
    srand(time(0));
    AuthSystem auth; auth.loadUsers();
    while(!auth.menu());

    Graph g(80); loadRoads("road_shape.csv",g);

    int patientLoc,hospitalLoc;
    cout<<"Enter Patient Location: "; cin>>patientLoc;
    cout<<"Enter Hospital (-1 auto): "; cin>>hospitalLoc;
    if(hospitalLoc==-1){ hospitalLoc=closestHospital(g,patientLoc); cout<<"Auto Hospital: "<<hospitalLoc<<"\n"; }

    cout<<"Ambulance: "<<closestAmbulance(patientLoc)<<"\n";

    cout<<"\nSelect Route Module:\n1. Risk-Avoid\n2. Hybrid\n3. Emergency-Fast\n4. Weather-Adaptive\n5. Fuel-Efficient\nChoice: ";
    int moduleChoice; cin>>moduleChoice;

    vector<int> parent(g.V); vector<double> dist;
    switch(moduleChoice){
        case 1: dist=dijkstraRiskAvoid(g,patientLoc,parent); break;
        case 2: dist=dijkstraHybrid(g,patientLoc,parent); break;
        case 3: dist=dijkstraEmergencyFast(g,patientLoc,parent); break;
        case 4: dist=dijkstraWeatherAdaptive(g,patientLoc,parent); break;
        case 5: dist=dijkstraFuelEfficient(g,patientLoc,parent); break;
        default: cout<<"Invalid choice, using Risk-Avoid\n"; dist=dijkstraRiskAvoid(g,patientLoc,parent);
    }

    vector<int> path;
    for(int cur=hospitalLoc;cur!=-1;cur=parent[cur]) path.push_back(cur);
    reverse(path.begin(),path.end());

    cout<<"\nPath: "; for(int x:path) cout<<x<<" ";
    double cost=totalCost(g,path);
    cout<<"\nCost: "<<cost<<"\n";

    checkEmergency(g,path,0.7);
    int level=severityLevel(g,path);
    alertSystem(level);
    if(needReroute(level)){ cout<<"REROUTING...\n"; dist=dijkstraHybrid(g,patientLoc,parent); }
    if(cost>500) sendEmailAlert();

    return 0;
}
